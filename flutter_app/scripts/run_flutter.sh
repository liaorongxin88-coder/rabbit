#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$SCRIPT_DIR/flutter_env.sh"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/run_flutter.sh <dev|test|release> [--debug|--profile|--release] [extra flutter run args...]

Examples:
  ./scripts/run_flutter.sh dev -d emulator-5554
  ./scripts/run_flutter.sh test --debug
  ./scripts/run_flutter.sh release --profile -d R58N...
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

run_mode="--debug"
extra_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug|--profile|--release)
      run_mode="$1"
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

flutter run "$run_mode" \
  --target=lib/main.dart \
  "${RABBIT_FLUTTER_ENV_ARGS[@]}" \
  "${extra_args[@]}"
