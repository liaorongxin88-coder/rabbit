#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$SCRIPT_DIR/flutter_env.sh"
source "$SCRIPT_DIR/toolchain_env.sh"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/build_release.sh <aab|apk|size> [extra flutter build args...]

Modes:
  aab   Build the store bundle with ABI delivery handled by the store.
  apk   Build one release APK per Android ABI for direct distribution.
  size  Build and analyze the arm64 release APK.

Release signing is read from these optional local environment variables:
  RABBIT_ANDROID_KEYSTORE_PATH
  RABBIT_ANDROID_KEYSTORE_PASSWORD
  RABBIT_ANDROID_KEY_ALIAS
  RABBIT_ANDROID_KEY_PASSWORD
USAGE
}

if [[ $# -eq 0 || "$1" == "-h" || "$1" == "--help" ]]; then
  usage
  exit 0
fi

mode="$1"
shift

if [[ -f "$RABBIT_FLUTTER_PROJECT_DIR/config/env/prod.env" ]]; then
  rabbit_resolve_env prod
else
  echo "Using legacy config/env/release.env; migrate to config/env/prod.env before publishing." >&2
  rabbit_resolve_env release
fi
rabbit_configure_android_toolchain
cd "$RABBIT_FLUTTER_PROJECT_DIR"

symbol_dir="build/symbols/prod"
release_args=(
  --release
  --target=lib/main.dart
  "${RABBIT_FLUTTER_ENV_ARGS[@]}"
)
symbol_args=(
  "${release_args[@]}"
  --split-debug-info="$symbol_dir"
)

case "$mode" in
  aab)
    "$RABBIT_FLUTTER_BIN" build appbundle "${symbol_args[@]}" "$@"
    ;;
  apk)
    "$RABBIT_FLUTTER_BIN" build apk "${symbol_args[@]}" --split-per-abi "$@"
    ;;
  size)
    "$RABBIT_FLUTTER_BIN" build apk "${release_args[@]}" \
      --target-platform=android-arm64 \
      --analyze-size \
      "$@"
    ;;
  *)
    echo "Unknown release build mode: $mode" >&2
    usage >&2
    exit 64
    ;;
esac
