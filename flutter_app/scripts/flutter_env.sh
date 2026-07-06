#!/usr/bin/env bash
set -euo pipefail

RABBIT_FLUTTER_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

rabbit_env_help() {
  cat <<'USAGE'
Environments:
  dev      Local Android emulator development backend.
  test     Test backend config, mapped to the Android staging flavor.
  release  Production backend config, mapped to the Android releaseEnv flavor.

Environment files:
  dev      -> config/env/dev.env
  test     -> config/env/test.env
  release  -> config/env/release.env
USAGE
}

rabbit_resolve_env() {
  local env_name="$1"

  case "$env_name" in
    dev)
      RABBIT_BUILD_ENV="dev"
      RABBIT_ANDROID_FLAVOR="dev"
      RABBIT_CONFIG_FILE="config/env/dev.env"
      ;;
    test)
      RABBIT_BUILD_ENV="test"
      RABBIT_ANDROID_FLAVOR="staging"
      RABBIT_CONFIG_FILE="config/env/test.env"
      ;;
    release)
      RABBIT_BUILD_ENV="release"
      RABBIT_ANDROID_FLAVOR="releaseEnv"
      RABBIT_CONFIG_FILE="config/env/release.env"
      ;;
    *)
      echo "Unknown environment: $env_name" >&2
      rabbit_env_help >&2
      exit 64
      ;;
  esac

  if [[ ! -f "$RABBIT_FLUTTER_PROJECT_DIR/$RABBIT_CONFIG_FILE" ]]; then
    echo "Missing environment file: $RABBIT_CONFIG_FILE" >&2
    if [[ "$RABBIT_ANDROID_FLAVOR" == "releaseEnv" ]]; then
      echo "Create it with: cp config/env/release.env.example config/env/release.env" >&2
      echo "Then edit RABBIT_API_BASE_URL before running the production environment." >&2
    fi
    exit 66
  fi

  RABBIT_FLUTTER_ENV_ARGS=(
    "--flavor=$RABBIT_ANDROID_FLAVOR"
    "--dart-define-from-file=$RABBIT_CONFIG_FILE"
    "--dart-define=RABBIT_BUILD_ENV=$RABBIT_BUILD_ENV"
  )
  RABBIT_FLUTTER_DART_DEFINE_ARGS=(
    "--dart-define-from-file=$RABBIT_CONFIG_FILE"
    "--dart-define=RABBIT_BUILD_ENV=$RABBIT_BUILD_ENV"
  )
}
