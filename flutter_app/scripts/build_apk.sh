#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$SCRIPT_DIR/flutter_env.sh"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/build_apk.sh <dev|test|release> [--debug|--profile|--release] [extra flutter build apk args...]

Examples:
  ./scripts/build_apk.sh dev --debug
  ./scripts/build_apk.sh test --debug
  ./scripts/build_apk.sh release --release --split-per-abi
USAGE
  rabbit_env_help
}

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 64
fi

env_name="$1"
shift

if [[ "$env_name" == "-h" || "$env_name" == "--help" ]]; then
  usage
  exit 0
fi

rabbit_resolve_env "$env_name"

build_mode="--release"
extra_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug|--profile|--release)
      build_mode="$1"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      extra_args+=("$1")
      ;;
  esac
  shift
done

cd "$RABBIT_FLUTTER_PROJECT_DIR"

flutter build apk "$build_mode" \
  --target=lib/main.dart \
  "${RABBIT_FLUTTER_ENV_ARGS[@]}" \
  "${extra_args[@]}"
