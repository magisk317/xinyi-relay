#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_BUILD="$ROOT_DIR/app/build.gradle.kts"
CORE_BUILD="$ROOT_DIR/core/build.gradle.kts"
HOOK_ENTRY_BUILD="$ROOT_DIR/hook/entry/build.gradle.kts"
MOBILE_UI_BUILD="$ROOT_DIR/mobile/ui/build.gradle.kts"
MOBILE_UI_SRC="$ROOT_DIR/mobile/ui/src"
MOBILE_FEATURE_DIR="$ROOT_DIR/mobile/feature"
RUNTIME_BUILD="$ROOT_DIR/runtime/build.gradle.kts"
RUNTIME_SRC="$ROOT_DIR/runtime/src"
RELAY_ANDROID_BUILD="$ROOT_DIR/relay/android/build.gradle.kts"
RELAY_SENDER_BUILD="$ROOT_DIR/relay/sender/build.gradle.kts"
XPBRIDGE_CORE_BUILD="$ROOT_DIR/xpbridge/core/build.gradle.kts"

violations=()

python3 "$ROOT_DIR/scripts/generate_sender_schema_contract.py" --check

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! rg -q "$pattern" "$file"; then
    violations+=("$message")
  fi
}

forbid_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if rg -q "$pattern" "$file"; then
    violations+=("$message")
  fi
}

check_mobile_feature_dependencies() {
  local feature_build
  while IFS= read -r -d '' feature_build; do
    local feature_name
    local feature_path
    feature_name="$(basename "$(dirname "$feature_build")")"
    feature_path=":mobile:feature:$feature_name"

    if rg -q 'project\(":mobile:ui"\)' "$feature_build"; then
      violations+=("$feature_path must not depend on :mobile:ui; navigation contracts stay in the shell module")
    fi

    if rg -q 'project\(":relay:sender"\)' "$feature_build"; then
      violations+=("$feature_path must depend on :relay:sender:api instead of the :relay:sender implementation module")
    fi

    local feature_deps
    mapfile -t feature_deps < <(rg -o 'project\(":mobile:feature:[^"]+"\)' "$feature_build" || true)
    local dependency
    for dependency in "${feature_deps[@]}"; do
      local dependency_path
      dependency_path="$(printf '%s\n' "$dependency" | sed -E 's/^project\("([^"]+)"\)$/\1/')"
      if [[ "$dependency_path" != ":mobile:feature:common" ]]; then
        violations+=("$feature_path must not depend directly on $dependency_path; use :mobile:feature:common, a lower contract/api module, or :mobile:ui composition")
      fi
    done
  done < <(find "$MOBILE_FEATURE_DIR" -mindepth 2 -maxdepth 2 -name build.gradle.kts -print0)
}

require_pattern "$APP_BUILD" 'implementation\(project\(":core"\)\)' \
  "app must depend directly on :core"
require_pattern "$APP_BUILD" 'implementation\(project\(":relay:android"\)\)' \
  "app must depend directly on :relay:android for platform adapter installation"
forbid_pattern "$APP_BUILD" 'implementation\(project\(":runtime"\)\)' \
  "app must not runtime-package :runtime directly"
forbid_pattern "$APP_BUILD" 'api\(project\(":runtime"\)\)' \
  "app must not expose :runtime directly"
forbid_pattern "$APP_BUILD" 'compileOnly\(project\(":runtime"\)\)' \
  "app must not compile against :runtime directly"
forbid_pattern "$APP_BUILD" 'implementation\(project\(":smscode-core:core"\)\)' \
  "app must not runtime-package :smscode-core:core directly"
forbid_pattern "$APP_BUILD" 'api\(project\(":smscode-core:core"\)\)' \
  "app must not expose :smscode-core:core directly"
require_pattern "$APP_BUILD" 'implementation\(project\(":hook:entry"\)\)' \
  "app must package :hook:entry for libxposed entrypoints"
forbid_pattern "$APP_BUILD" 'implementation\(project\(":smscode-core:xposed"\)\)' \
  "app must not package :smscode-core:xposed directly"

require_pattern "$CORE_BUILD" 'implementation\(project\(":runtime"\)\)' \
  "core must depend on :runtime as implementation"
forbid_pattern "$CORE_BUILD" 'api\(project\(":runtime"\)\)' \
  "core must not expose :runtime transitively"
require_pattern "$CORE_BUILD" 'implementation\(project\(":relay:engine:api"\)\)' \
  "core must depend on :relay:engine:api for engine contracts"
require_pattern "$CORE_BUILD" 'implementation\(project\(":smscode-core:xposed"\)\)' \
  "core runtime bridge may depend directly on :smscode-core:xposed during the split"
forbid_pattern "$CORE_BUILD" 'project\(":xpbridge:core"\)' \
  "core must not depend on :xpbridge:core directly"

forbid_pattern "$HOOK_ENTRY_BUILD" 'project\(":core"\)' \
  "hook/entry must not depend on :core directly"

forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":relay:engine"\)' \
  "xpbridge/core must not depend on :relay:engine implementation directly"
forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":runtime"\)' \
  "xpbridge/core must not depend on :runtime directly"
forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":relay:android"\)' \
  "xpbridge/core must not depend on :relay:android directly"
forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":smscode-core:domain"\)' \
  "xpbridge/core must not depend on :smscode-core:domain directly"
forbid_pattern "$XPBRIDGE_CORE_BUILD" 'androidx\.compose' \
  "xpbridge/core must not depend on Compose runtime"

forbid_pattern "$MOBILE_UI_BUILD" 'project\(":xpbridge:core"\)' \
  "mobile/ui must not depend on :xpbridge:core directly"
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":runtime"\)' \
  "mobile/ui must not depend on :runtime directly"
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":relay:engine"\)' \
  "mobile/ui must depend on :relay:engine:api, not :relay:engine implementation"
require_pattern "$MOBILE_UI_BUILD" 'implementation\(project\(":relay:engine:api"\)\)' \
  "mobile/ui must depend on :relay:engine:api for engine contracts"
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":relay:sender"\)' \
  "mobile/ui must depend on :relay:sender:api, not :relay:sender implementation"
require_pattern "$MOBILE_UI_BUILD" 'implementation\(project\(":relay:sender:api"\)\)' \
  "mobile/ui must depend on :relay:sender:api for sender configuration contracts"
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":smscode-core:verification"\)' \
  "mobile/ui must not depend on :smscode-core:verification directly"
forbid_pattern "$MOBILE_UI_SRC" '^\s*import\s+io\.github\.magisk317\.relay\.sender\.(BarkUtils|DingtalkGroupRobotUtils|DingtalkInnerRobotUtils|EmailUtils|FeishuAppUtils|FeishuUtils|GotifyUtils|NtfyUtils|PushplusUtils|ServerchanUtils|PushdeerUtils|SmsUtils|SocketUtils|TelegramUtils|UrlSchemeUtils|WebhookUtils|WeworkAgentUtils|WeworkRobotUtils|DefaultSenderDispatcher|SenderRuntimeInstaller)' \
  "mobile/ui must use stable sender APIs/facades instead of importing relay/sender implementation classes"
forbid_pattern "$MOBILE_UI_SRC" '^\s*import\s+io\.github\.magisk317\.relay\.sender\.config\.' \
  "mobile/ui sender forms must use SenderSettingDrafts/SenderSettingSchemas instead of importing concrete sender config models"
check_mobile_feature_dependencies
forbid_pattern "$MOBILE_FEATURE_DIR" '^\s*import\s+io\.github\.magisk317\.relay\.ui\.nav\.' \
  "mobile feature modules must not import mobile/ui navigation contracts; receive callbacks from :mobile:ui instead"
forbid_pattern "$MOBILE_FEATURE_DIR" '^\s*import\s+androidx\.navigation\.' \
  "mobile feature modules must not own navigation graphs or controllers; route composition stays in :mobile:ui"

forbid_pattern "$RELAY_ANDROID_BUILD" 'project\(":relay:engine"\)' \
  "relay/android must depend on :relay:engine:api, not :relay:engine implementation"
require_pattern "$RELAY_ANDROID_BUILD" 'implementation\(project\(":relay:engine:api"\)\)' \
  "relay/android must depend on :relay:engine:api for engine contracts"
require_pattern "$RELAY_ANDROID_BUILD" 'implementation\(project\(":relay:sender"\)\)' \
  "relay/android may bridge relay/sender but must not expose it transitively"
forbid_pattern "$RELAY_ANDROID_BUILD" 'api\(project\(":relay:sender"\)\)' \
  "relay/android must not expose relay/sender transitively"

forbid_pattern "$RELAY_SENDER_BUILD" 'project\(":relay:engine"\)' \
  "relay/sender must not depend on :relay:engine implementation directly"
require_pattern "$RELAY_SENDER_BUILD" 'implementation\(project\(":relay:engine:api"\)\)' \
  "relay/sender must depend on :relay:engine:api for engine contracts"
require_pattern "$RELAY_SENDER_BUILD" 'implementation\(project\(":relay:net"\)\)' \
  "relay/sender must depend on :relay:net for shared HTTP helpers"

require_pattern "$RUNTIME_BUILD" 'implementation\(project\(":smscode-core:domain"\)\)' \
  "runtime must depend on :smscode-core:domain"
forbid_pattern "$RUNTIME_BUILD" 'project\(":smscode-core:core"\)' \
  "runtime must stop depending on :smscode-core:core"
forbid_pattern "$RUNTIME_BUILD" 'project\(":relay:sender"\)' \
  "runtime must not depend on :relay:sender directly"
forbid_pattern "$RUNTIME_SRC" '^\s*import\s+io\.github\.magisk317\.relay\.sender\.' \
  "runtime must use relay/engine:api SenderDispatcher services instead of importing relay/sender implementation packages"

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Module boundary verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Module boundary verification passed."
