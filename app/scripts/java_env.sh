#!/usr/bin/env bash

RABBIT_JAVA_ENV_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
RABBIT_JAVA_REQUIRED_MAJOR="${RABBIT_JAVA_REQUIRED_MAJOR:-21}"
RABBIT_TOOLCHAIN_CONFIG_FILE="${RABBIT_TOOLCHAIN_CONFIG_FILE:-config/env/toolchain.local.env}"

rabbit_java_help() {
  cat <<'USAGE'
Android toolchain configuration:
  RABBIT_JAVA_HOME                 Explicit JDK 21 home (highest priority).
  RABBIT_TOOLCHAIN_CONFIG_FILE     Project-relative or absolute local config file.

Default local config:
  config/env/toolchain.local.env

When no explicit value is provided, the scripts try JAVA_HOME, standard
Homebrew/macOS/Linux locations, and Gradle's downloaded JDK cache.
USAGE
}

rabbit_java_major() {
  local java_home="$1"

  "$java_home/bin/java" -version 2>&1 | awk -F '[".]' '
    /version/ {
      if ($2 == "1") {
        print $3
      } else {
        print $2
      }
      exit
    }
  '
}

rabbit_normalize_java_home() {
  local java_home="$1"

  if [[ ! -x "$java_home/bin/java" ]]; then
    return 1
  fi

  (cd "$java_home" && pwd -L)
}

rabbit_load_toolchain_config() {
  local config_file="$RABBIT_TOOLCHAIN_CONFIG_FILE"
  local environment_java_home="${RABBIT_JAVA_HOME:-}"
  local environment_flutter_bin="${RABBIT_FLUTTER_BIN:-}"
  local environment_flutter_home="${RABBIT_FLUTTER_HOME:-}"
  local environment_android_sdk_root="${RABBIT_ANDROID_SDK_ROOT:-}"
  local environment_android_sdk_env="${ANDROID_SDK_ROOT:-}"
  local environment_android_home="${ANDROID_HOME:-}"
  local restore_allexport=0

  if [[ "${RABBIT_TOOLCHAIN_CONFIG_LOADED:-0}" == "1" ]]; then
    return
  fi

  if [[ "$config_file" != /* ]]; then
    config_file="$RABBIT_JAVA_ENV_PROJECT_DIR/$config_file"
  fi
  if [[ ! -f "$config_file" ]]; then
    return
  fi

  if [[ $- == *a* ]]; then
    restore_allexport=1
  else
    set -a
  fi
  # shellcheck disable=SC1090
  source "$config_file"
  if [[ "$restore_allexport" == "0" ]]; then
    set +a
  fi

  if [[ -n "$environment_java_home" ]]; then
    RABBIT_JAVA_HOME="$environment_java_home"
  fi
  if [[ -n "$environment_flutter_bin" ]]; then
    RABBIT_FLUTTER_BIN="$environment_flutter_bin"
  fi
  if [[ -n "$environment_flutter_home" ]]; then
    RABBIT_FLUTTER_HOME="$environment_flutter_home"
  fi
  if [[ -n "$environment_android_sdk_root" ]]; then
    RABBIT_ANDROID_SDK_ROOT="$environment_android_sdk_root"
  fi
  if [[ -n "$environment_android_sdk_env" ]]; then
    ANDROID_SDK_ROOT="$environment_android_sdk_env"
  fi
  if [[ -n "$environment_android_home" ]]; then
    ANDROID_HOME="$environment_android_home"
  fi

  RABBIT_TOOLCHAIN_CONFIG_LOADED=1
}

rabbit_resolve_java_home() {
  local configured_home=""
  local candidate=""
  local normalized_home=""
  local java_major=""
  local detected_home=""
  local candidates=()

  rabbit_load_toolchain_config
  configured_home="${RABBIT_JAVA_HOME:-}"

  if [[ -n "$configured_home" ]]; then
    normalized_home="$(rabbit_normalize_java_home "$configured_home" || true)"
    if [[ -z "$normalized_home" ]]; then
      echo "Invalid RABBIT_JAVA_HOME: $configured_home" >&2
      return 69
    fi
    java_major="$(rabbit_java_major "$normalized_home")"
    if [[ "$java_major" != "$RABBIT_JAVA_REQUIRED_MAJOR" ]]; then
      echo "Rabbit Android builds require JDK $RABBIT_JAVA_REQUIRED_MAJOR; found JDK ${java_major:-unknown} at $normalized_home" >&2
      return 69
    fi
    printf '%s\n' "$normalized_home"
    return
  fi

  candidates+=("${JAVA_HOME:-}" "${JAVA_HOME_21_X64:-}" "${JDK_HOME:-}")

  if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
    detected_home="$(/usr/libexec/java_home -v "$RABBIT_JAVA_REQUIRED_MAJOR" 2>/dev/null || true)"
    candidates+=("$detected_home")
  fi

  candidates+=(
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "$HOME/android-studio/jbr"
    "/opt/android-studio/jbr"
    "/c/Program Files/Android/Android Studio/jbr"
  )

  for candidate in "${candidates[@]}"; do
    [[ -n "$candidate" ]] || continue
    normalized_home="$(rabbit_normalize_java_home "$candidate" || true)"
    [[ -n "$normalized_home" ]] || continue
    java_major="$(rabbit_java_major "$normalized_home")"
    if [[ "$java_major" == "$RABBIT_JAVA_REQUIRED_MAJOR" ]]; then
      printf '%s\n' "$normalized_home"
      return
    fi
  done

  for candidate in \
    "$HOME"/.gradle/jdks/*/Contents/Home \
    "$HOME"/.gradle/jdks/*/*/Contents/Home \
    "$HOME"/.sdkman/candidates/java/21* \
    "$HOME"/.asdf/installs/java/*21* \
    "/c/Program Files/Eclipse Adoptium"/jdk-21* \
    "/c/Program Files/Microsoft"/jdk-21* \
    "/c/Program Files/Java"/jdk-21* \
    /usr/lib/jvm/java-21-openjdk* \
    /usr/lib/jvm/temurin-21*; do
    normalized_home="$(rabbit_normalize_java_home "$candidate" || true)"
    [[ -n "$normalized_home" ]] || continue
    java_major="$(rabbit_java_major "$normalized_home")"
    if [[ "$java_major" == "$RABBIT_JAVA_REQUIRED_MAJOR" ]]; then
      printf '%s\n' "$normalized_home"
      return
    fi
  done

  echo "JDK $RABBIT_JAVA_REQUIRED_MAJOR was not found." >&2
  echo "Set RABBIT_JAVA_HOME or create config/env/toolchain.local.env from its example." >&2
  return 69
}

rabbit_configure_java() {
  local resolved_home=""
  local gradle_java_option=""

  resolved_home="$(rabbit_resolve_java_home)" || return $?
  export RABBIT_JAVA_HOME="$resolved_home"
  export JAVA_HOME="$resolved_home"
  export PATH="$JAVA_HOME/bin:$PATH"

  gradle_java_option="-Dorg.gradle.java.home=\"$JAVA_HOME\""
  if [[ " ${GRADLE_OPTS:-} " != *" $gradle_java_option "* ]]; then
    export GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }$gradle_java_option"
  fi
}
