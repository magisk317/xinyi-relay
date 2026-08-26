param(
  [Parameter(Mandatory = $true)][string]$ArtifactSuffix,
  [Parameter(Mandatory = $true)][string]$RustTarget,
  [Parameter(Mandatory = $true)][string]$BundleArgs,
  [Parameter(Mandatory = $true)][string]$BundleDir
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$DesktopDir = Join-Path $RootDir "frontend\desktop"
$ArtifactDir = Join-Path $RootDir "desktop-artifacts\$ArtifactSuffix"

function Add-PathFirst([string]$PathValue) {
  $env:PATH = "$PathValue;$env:PATH"
}

function Install-Node24 {
  $useExisting = $false
  if (Get-Command node -ErrorAction SilentlyContinue) {
    $major = node -p "process.versions.node.split('.')[0]"
    if ($major -eq "24") {
      $useExisting = $true
    }
  }
  if ($useExisting) {
    return
  }

  $channel = if ($env:XINYI_NODE_RELEASE_CHANNEL) { $env:XINYI_NODE_RELEASE_CHANNEL } else { "latest-v24.x" }
  $baseUrl = "https://nodejs.org/dist/$channel"
  $sums = (Invoke-WebRequest -UseBasicParsing "$baseUrl/SHASUMS256.txt").Content -split "`n"
  $archive = (($sums | Where-Object { $_ -match "node-v.*-win-x64\.zip" } | Select-Object -First 1) -split "\s+")[1]
  if (-not $archive) {
    throw "Failed to resolve Node.js 24 win-x64 archive"
  }

  $nodeRoot = Join-Path $RootDir ".ci-node"
  $zipPath = Join-Path $RootDir $archive
  Remove-Item -Recurse -Force $nodeRoot -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force $nodeRoot | Out-Null
  Invoke-WebRequest -UseBasicParsing "$baseUrl/$archive" -OutFile $zipPath
  Expand-Archive -Force $zipPath -DestinationPath $nodeRoot
  Remove-Item -Force $zipPath
  $nodeDir = Get-ChildItem -Directory $nodeRoot | Select-Object -First 1
  Add-PathFirst $nodeDir.FullName
}

function Install-Rust {
  if (-not (Get-Command rustup -ErrorAction SilentlyContinue)) {
    $rustup = Join-Path $RootDir "rustup-init.exe"
    Invoke-WebRequest -UseBasicParsing "https://win.rustup.rs/x86_64" -OutFile $rustup
    & $rustup -y --profile minimal --default-toolchain stable
    Remove-Item -Force $rustup
  }
  Add-PathFirst (Join-Path $env:USERPROFILE ".cargo\bin")
  rustup target add $RustTarget
}

function Replace-Once([string]$PathValue, [string]$Pattern, [string]$Replacement) {
  $text = Get-Content -Raw $PathValue
  $regex = [regex]::new($Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
  if (-not $regex.IsMatch($text)) {
    throw "Failed to update $PathValue"
  }
  $next = $regex.Replace($text, $Replacement, 1)
  Set-Content -NoNewline -Path $PathValue -Value $next
}

function Sync-DesktopVersion {
  $versionFile = Join-Path $RootDir "gradle\libs.versions.toml"
  $packageJson = Join-Path $DesktopDir "package.json"
  $cargoToml = Join-Path $DesktopDir "src-tauri\Cargo.toml"
  $tauriConf = Join-Path $DesktopDir "src-tauri\tauri.conf.json"

  $versionMatch = Select-String -Path $versionFile -Pattern '^versionName\s*=\s*"([^"]+)"' | Select-Object -First 1
  if (-not $versionMatch) {
    throw "Failed to parse versionName from $versionFile"
  }
  $version = $versionMatch.Matches[0].Groups[1].Value

  Replace-Once $packageJson '"version"\s*:\s*"[^"]*"' "`"version`": `"$version`""
  Replace-Once $cargoToml '^version\s*=\s*"[^"]*"' "version = `"$version`""
  Replace-Once $tauriConf '"version"\s*:\s*"[^"]*"' "`"version`": `"$version`""
  Replace-Once $tauriConf '"version"\s*:\s*"([0-9]+\.[0-9]+\.[0-9]+)-[a-zA-Z0-9]+"' "`"version`": `"`$1`""
}

function Collect-Artifacts {
  Remove-Item -Recurse -Force $ArtifactDir -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force $ArtifactDir | Out-Null
  $fullBundleDir = Join-Path $RootDir $BundleDir
  $files = Get-ChildItem -Path $fullBundleDir -Recurse -File -Include *.exe,*.msix,*.msi
  foreach ($file in $files) {
    Copy-Item -Force $file.FullName -Destination $ArtifactDir
  }
  if (-not (Get-ChildItem -Path $ArtifactDir -File -ErrorAction SilentlyContinue)) {
    throw "No desktop artifacts found in $fullBundleDir"
  }
}

Install-Node24
Install-Rust

# Single source of truth: Bun version follows the desktop package.json
# packageManager field. The Windows runner uses this PowerShell equivalent of
# magisk-ci-toolkit's ci/ensure_bun.sh.
$packageManager = node -p "require('$($DesktopDir.Replace('\', '\\'))/package.json').packageManager"
if ($packageManager -notmatch '^bun@') {
  throw "packageManager in desktop package.json is not bun@x.y.z (got '$packageManager')"
}
$bunVersion = ($packageManager -replace '^bun@', '') -replace '\+.*$', ''
$currentBun = $null
try { $currentBun = (bun --version) 2>$null } catch { $currentBun = $null }
if ($currentBun -ne $bunVersion) {
  npm install --global --no-audit --no-fund "bun@$bunVersion"
}
if ((bun --version) -ne $bunVersion) {
  throw "Expected Bun $bunVersion, got $(bun --version)"
}

Sync-DesktopVersion
Set-Location $DesktopDir
bun install --frozen-lockfile
bun run build
$buildArgs = $BundleArgs -split "\s+"
& bun ./scripts/with-system-pkg-config.mjs tauri build @buildArgs
Collect-Artifacts
