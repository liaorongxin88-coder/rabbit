#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/toolchain_env.sh"

usage() {
  cat <<'USAGE'
Usage: ./rabbit <command> [arguments]

Machine setup and diagnostics:
  bootstrap                 Configure the Android toolchain and fetch packages.
  doctor                    Resolve tools and print Flutter's full diagnostics.
  deps                      Fetch Flutter packages.

Development and verification:
  run <env> [args...]       Run dev, test, prod, or legacy release environment.
  analyze                   Run Flutter static analysis.
  test [env] [args...]      Run unit/widget tests; defaults to test environment.
  check                     Run static analysis and unit/widget tests.
  verify                    Run check and build a dev debug APK.
  devices                   List Flutter target devices.
  gradle [args...]          Run the project Gradle wrapper with dynamic JDK 21.

Build and delivery:
  apk <env> [args...]       Build an APK through scripts/build_apk.sh.
  release <aab|apk|size>    Build a production delivery artifact.
  e2e                       Run the Android end-to-end workflow.

Examples:
  ./rabbit bootstrap
  ./rabbit run dev -d emulator-5554
  ./rabbit test
  ./rabbit apk test --release
  ./rabbit release aab
USAGE
}

require_no_args() {
  local command_name="$1"
  shift

  if [[ $# -gt 0 ]]; then
    echo "$command_name does not accept arguments." >&2
    usage >&2
    exit 64
  fi
}

command_name="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

case "$command_name" in
  help|-h|--help)
    usage
    ;;
  bootstrap|setup)
    require_no_args "$command_name" "$@"
    "$SCRIPT_DIR/setup_android_env.sh"
    rabbit_configure_flutter
    cd "$PROJECT_DIR"
    "$RABBIT_FLUTTER_BIN" pub get
    ;;
  doctor)
    require_no_args "$command_name" "$@"
    rabbit_configure_android_toolchain
    cat <<EOF
Rabbit toolchain:
  Flutter: $RABBIT_FLUTTER_BIN
  Java: $JAVA_HOME (JDK $(rabbit_java_major "$JAVA_HOME"))
  Android SDK: $ANDROID_SDK_ROOT
  Local overrides: $RABBIT_TOOLCHAIN_CONFIG_FILE
EOF
    cd "$PROJECT_DIR"
    "$RABBIT_FLUTTER_BIN" --version
    "$RABBIT_FLUTTER_BIN" doctor -v
    ;;
  deps|get)
    require_no_args "$command_name" "$@"
    rabbit_configure_flutter
    cd "$PROJECT_DIR"
    "$RABBIT_FLUTTER_BIN" pub get
    ;;
  analyze)
    require_no_args "$command_name" "$@"
    rabbit_configure_flutter
    cd "$PROJECT_DIR"
    "$RABBIT_FLUTTER_BIN" analyze
    ;;
  test)
    "$SCRIPT_DIR/test_flutter.sh" "$@"
    ;;
  check)
    require_no_args "$command_name" "$@"
    rabbit_configure_flutter
    cd "$PROJECT_DIR"
    "$RABBIT_FLUTTER_BIN" analyze
    "$SCRIPT_DIR/test_flutter.sh"
    ;;
  verify)
    require_no_args "$command_name" "$@"
    "$SCRIPT_DIR/rabbit.sh" check
    "$SCRIPT_DIR/build_apk.sh" dev --debug
    ;;
  run)
    "$SCRIPT_DIR/run_flutter.sh" "$@"
    ;;
  devices)
    require_no_args "$command_name" "$@"
    rabbit_configure_android_toolchain
    "$RABBIT_FLUTTER_BIN" devices
    ;;
  gradle)
    rabbit_configure_android_toolchain
    cd "$PROJECT_DIR/android"
    ./gradlew "$@"
    ;;
  apk|build)
    "$SCRIPT_DIR/build_apk.sh" "$@"
    ;;
  release)
    "$SCRIPT_DIR/build_release.sh" "$@"
    ;;
  e2e)
    require_no_args "$command_name" "$@"
    "$SCRIPT_DIR/android_e2e.sh"
    ;;
  *)
    echo "Unknown Rabbit command: $command_name" >&2
    usage >&2
    exit 64
    ;;
esac
