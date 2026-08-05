#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/java_env.sh"

usage() {
  cat <<'USAGE'
Usage: ./scripts/setup_android_env.sh

Detects JDK 21, configures Flutter to use it, and writes Android Studio's local
GRADLE_LOCAL_JAVA_HOME value to android/.gradle/config.properties.
USAGE
  rabbit_java_help
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -gt 0 ]]; then
  usage >&2
  exit 64
fi
if ! command -v flutter >/dev/null 2>&1; then
  echo "Missing required command: flutter" >&2
  exit 69
fi

rabbit_configure_java
flutter config --jdk-dir="$JAVA_HOME"

gradle_config_dir="$PROJECT_DIR/android/.gradle"
gradle_config_file="$gradle_config_dir/config.properties"
mkdir -p "$gradle_config_dir"

temporary_config="$(mktemp "$gradle_config_dir/config.properties.XXXXXX")"
trap 'rm -f "$temporary_config"' EXIT

if [[ -f "$gradle_config_file" ]]; then
  awk -v java_home="$JAVA_HOME" '
    BEGIN { updated = 0 }
    /^java\.home=/ {
      if (!updated) {
        print "java.home=" java_home
        updated = 1
      }
      next
    }
    { print }
    END {
      if (!updated) {
        print "java.home=" java_home
      }
    }
  ' "$gradle_config_file" > "$temporary_config"
else
  printf 'java.home=%s\n' "$JAVA_HOME" > "$temporary_config"
fi

mv "$temporary_config" "$gradle_config_file"
trap - EXIT

(
  cd "$PROJECT_DIR/android"
  ./gradlew --stop >/dev/null
)

cat <<EOF
Android environment configured with JDK $(rabbit_java_major "$JAVA_HOME"):
  JAVA_HOME=$JAVA_HOME
  Flutter jdk-dir=$JAVA_HOME
  GRADLE_LOCAL_JAVA_HOME=$gradle_config_file

In Android Studio, select Gradle JDK: GRADLE_LOCAL_JAVA_HOME.
Restart Android Studio before running the app again.
EOF
