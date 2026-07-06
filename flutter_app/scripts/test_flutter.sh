#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$SCRIPT_DIR/flutter_env.sh"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/test_flutter.sh [dev|test|release] [extra flutter test args...]

Examples:
  ./scripts/test_flutter.sh
  ./scripts/test_flutter.sh test
  ./scripts/test_flutter.sh dev --name phone
USAGE
  rabbit_env_help
}

env_name="test"

if [[ $# -gt 0 ]]; then
  case "$1" in
    dev|test|release)
      env_name="$1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
  esac
fi

rabbit_resolve_env "$env_name"

cd "$RABBIT_FLUTTER_PROJECT_DIR"

flutter test \
  "${RABBIT_FLUTTER_DART_DEFINE_ARGS[@]}" \
  "$@"
