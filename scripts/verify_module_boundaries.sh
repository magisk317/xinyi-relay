#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_BUILD="$ROOT_DIR/app/build.gradle.kts"
CORE_BUILD="$ROOT_DIR/core/build.gradle.kts"
HOOK_ENTRY_BUILD="$ROOT_DIR/hook/entry/build.gradle.kts"
MOBILE_UI_BUILD="$ROOT_DIR/mobile/ui/build.gradle.kts"
MOBILE_UI_SRC="$ROOT_DIR/mobile/ui/src"
RUNTIME_BUILD="$ROOT_DIR/runtime/build.gradle.kts"
RUNTIME_SRC="$ROOT_DIR/runtime/src"
RELAY_ANDROID_BUILD="$ROOT_DIR/relay/android/build.gradle.kts"
RELAY_SENDER_BUILD="$ROOT_DIR/relay/sender/build.gradle.kts"
XPBRIDGE_CORE_BUILD="$ROOT_DIR/xpbridge/core/build.gradle.kts"

violations=()

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
forbid_pattern "$APP_BUILD" 'implementation\(project\(":smscode-core:smscode-xposed-core"\)\)' \
  "app must not package :smscode-core:smscode-xposed-core directly"

require_pattern "$CORE_BUILD" 'implementation\(project\(":runtime"\)\)' \
  "core must depend on :runtime as implementation"
forbid_pattern "$CORE_BUILD" 'api\(project\(":runtime"\)\)' \
  "core must not expose :runtime transitively"
require_pattern "$CORE_BUILD" 'implementation\(project\(":relay:engine:api"\)\)' \
  "core must depend on :relay:engine:api for engine contracts"
require_pattern "$CORE_BUILD" 'implementation\(project\(":smscode-core:smscode-xposed-core"\)\)' \
  "core runtime bridge may depend directly on :smscode-core:smscode-xposed-core during the split"
forbid_pattern "$CORE_BUILD" 'project\(":xpbridge:core"\)' \
  "core must not depend on :xpbridge:core directly"

forbid_pattern "$HOOK_ENTRY_BUILD" 'project\(":core"\)' \
  "hook/entry must not depend on :core directly"

forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":relay:engine"\)' \
  "xpbridge/core must not depend on :relay:engine implementation directly"
forbid_pattern "$XPBRIDGE_CORE_BUILD" 'project\(":smscode-core:smscode-domain"\)' \
  "xpbridge/core must not depend on :smscode-core:smscode-domain directly"
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
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":smscode-core:smscode-verification-core"\)' \
  "mobile/ui must not depend on :smscode-core:smscode-verification-core directly"
forbid_pattern "$MOBILE_UI_SRC" '^\s*import\s+io\.github\.magisk317\.relay\.sender\.(BarkUtils|DingtalkGroupRobotUtils|DingtalkInnerRobotUtils|EmailUtils|FeishuAppUtils|FeishuUtils|GotifyUtils|NtfyUtils|PushplusUtils|ServerchanUtils|SmsUtils|SocketUtils|TelegramUtils|UrlSchemeUtils|WebhookUtils|WeworkAgentUtils|WeworkRobotUtils|DefaultSenderDispatcher|SenderRuntimeInstaller)' \
  "mobile/ui must use stable sender APIs/facades instead of importing relay/sender implementation classes"
forbid_pattern "$MOBILE_UI_SRC" '^\s*import\s+io\.github\.magisk317\.relay\.sender\.config\.' \
  "mobile/ui sender forms must use SenderSettingDrafts/SenderSettingSchemas instead of importing concrete sender config models"

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

require_pattern "$RUNTIME_BUILD" 'implementation\(project\(":smscode-core:smscode-domain"\)\)' \
  "runtime must depend on :smscode-core:smscode-domain"
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
