#!/usr/bin/env bash

RABBIT_TOOLCHAIN_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
RABBIT_TOOLCHAIN_PROJECT_DIR="$(cd "$RABBIT_TOOLCHAIN_SCRIPT_DIR/.." && pwd -P)"

# shellcheck source=java_env.sh
source "$RABBIT_TOOLCHAIN_SCRIPT_DIR/java_env.sh"

rabbit_toolchain_help() {
  cat <<'USAGE'
Portable toolchain configuration:
  RABBIT_FLUTTER_BIN             Explicit Flutter executable.
  RABBIT_FLUTTER_HOME            Flutter SDK root containing bin/flutter.
  RABBIT_JAVA_HOME               Explicit JDK 21 home.
  RABBIT_ANDROID_SDK_ROOT        Explicit Android SDK root.
  RABBIT_TOOLCHAIN_CONFIG_FILE   Project-relative or absolute local config.

Default local config:
  config/env/toolchain.local.env

Rabbit-specific environment variables take priority over the local config.
The resolver then checks standard tool variables, a project FVM SDK, PATH,
Android local.properties, and common locations on macOS, Linux, and Git Bash.
USAGE
}

rabbit_normalize_executable() {
  local executable="$1"
  local executable_dir=""

  if [[ "$executable" != */* ]]; then
    executable="$(command -v "$executable" 2>/dev/null || true)"
  fi
  if [[ -z "$executable" || ! -x "$executable" ]]; then
    return 1
  fi

  executable_dir="$(cd "$(dirname "$executable")" && pwd -P)"
  printf '%s/%s\n' "$executable_dir" "$(basename "$executable")"
}

rabbit_resolve_flutter_bin() {
  local configured_bin=""
  local candidate=""
  local normalized_bin=""
  local candidates=()

  rabbit_load_toolchain_config

  configured_bin="${RABBIT_FLUTTER_BIN:-}"
  if [[ -z "$configured_bin" && -n "${RABBIT_FLUTTER_HOME:-}" ]]; then
    configured_bin="$RABBIT_FLUTTER_HOME/bin/flutter"
  fi
  if [[ -n "$configured_bin" ]]; then
    normalized_bin="$(rabbit_normalize_executable "$configured_bin" || true)"
    if [[ -z "$normalized_bin" ]]; then
      echo "Invalid Rabbit Flutter executable: $configured_bin" >&2
      return 69
    fi
    printf '%s\n' "$normalized_bin"
    return
  fi

  candidates+=(
    "$RABBIT_TOOLCHAIN_PROJECT_DIR/.fvm/flutter_sdk/bin/flutter"
    "$(command -v flutter 2>/dev/null || true)"
    "$HOME/fvm/default/bin/flutter"
    "$HOME/development/flutter/bin/flutter"
    "/opt/homebrew/bin/flutter"
    "/usr/local/bin/flutter"
  )

  for candidate in "${candidates[@]}"; do
    [[ -n "$candidate" ]] || continue
    normalized_bin="$(rabbit_normalize_executable "$candidate" || true)"
    if [[ -n "$normalized_bin" ]]; then
      printf '%s\n' "$normalized_bin"
      return
    fi
  done

  echo "Flutter was not found." >&2
  echo "Add Flutter to PATH or set RABBIT_FLUTTER_BIN in config/env/toolchain.local.env." >&2
  return 69
}

rabbit_configure_flutter() {
  local resolved_bin=""

  resolved_bin="$(rabbit_resolve_flutter_bin)" || return $?
  export RABBIT_FLUTTER_BIN="$resolved_bin"
  export PATH="$(dirname "$RABBIT_FLUTTER_BIN"):$PATH"
}

rabbit_android_sdk_from_local_properties() {
  local properties_file="$RABBIT_TOOLCHAIN_PROJECT_DIR/android/local.properties"
  local sdk_path=""

  if [[ ! -f "$properties_file" ]]; then
    return
  fi

  sdk_path="$(sed -n 's/^sdk\.dir=//p' "$properties_file" | sed -n '1p')"
  sdk_path="${sdk_path//\\\\/\\}"
  sdk_path="${sdk_path//\\:/:}"
  sdk_path="${sdk_path//\\ / }"

  if [[ "$sdk_path" =~ ^[A-Za-z]:\\ ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$sdk_path"
  else
    printf '%s\n' "$sdk_path"
  fi
}

rabbit_normalize_android_sdk() {
  local sdk_root="$1"

  if [[ "$sdk_root" =~ ^[A-Za-z]:[\\/] ]] && command -v cygpath >/dev/null 2>&1; then
    sdk_root="$(cygpath -u "$sdk_root")"
  fi
  if [[ -z "$sdk_root" || ! -d "$sdk_root" ]]; then
    return 1
  fi
  if [[ ! -x "$sdk_root/platform-tools/adb" && ! -x "$sdk_root/platform-tools/adb.exe" ]]; then
    return 1
  fi

  (cd "$sdk_root" && pwd -P)
}

rabbit_resolve_android_sdk() {
  local configured_sdk=""
  local candidate=""
  local normalized_sdk=""
  local candidates=()

  rabbit_load_toolchain_config

  configured_sdk="${RABBIT_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
  if [[ -n "$configured_sdk" ]]; then
    normalized_sdk="$(rabbit_normalize_android_sdk "$configured_sdk" || true)"
    if [[ -z "$normalized_sdk" ]]; then
      echo "Invalid Android SDK root: $configured_sdk" >&2
      return 69
    fi
    printf '%s\n' "$normalized_sdk"
    return
  fi

  candidates+=(
    "$(rabbit_android_sdk_from_local_properties)"
    "$HOME/Library/Android/sdk"
    "$HOME/Android/Sdk"
    "${LOCALAPPDATA:-}/Android/Sdk"
    "/c/Users/${USERNAME:-}/AppData/Local/Android/Sdk"
  )

  for candidate in "${candidates[@]}"; do
    [[ -n "$candidate" ]] || continue
    normalized_sdk="$(rabbit_normalize_android_sdk "$candidate" || true)"
    if [[ -n "$normalized_sdk" ]]; then
      printf '%s\n' "$normalized_sdk"
      return
    fi
  done

  echo "Android SDK was not found." >&2
  echo "Set RABBIT_ANDROID_SDK_ROOT in config/env/toolchain.local.env." >&2
  return 69
}

rabbit_configure_android_sdk() {
  local resolved_sdk=""

  resolved_sdk="$(rabbit_resolve_android_sdk)" || return $?
  export RABBIT_ANDROID_SDK_ROOT="$resolved_sdk"
  export ANDROID_SDK_ROOT="$resolved_sdk"
  export ANDROID_HOME="$resolved_sdk"
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
}

rabbit_configure_android_toolchain() {
  rabbit_configure_flutter
  rabbit_configure_java
  rabbit_configure_android_sdk
}
