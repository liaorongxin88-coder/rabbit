#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_PATH="${1:-}"
TARGET_IMAGE="${2:-}"
PUBLIC_URL="${3:-}"
ROLLBACK_MODE="${4:-manual}"
SOURCE_SHA="${5:-}"
OVERLAY_FILE="${6:-}"
BACKUP_PATH="${7:-}"

fail() {
  echo "activate-backend: $*" >&2
  exit 1
}

for command in docker gzip curl install; do
  command -v "$command" >/dev/null 2>&1 || fail "missing required command: $command"
done

[[ "$DEPLOY_PATH" == /* && -d "$DEPLOY_PATH" ]] || fail "invalid DEPLOY_PATH"
[[ "$BACKUP_PATH" == /* ]] || fail "invalid BACKUP_PATH"
[[ "$TARGET_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || fail "target image is not immutable"
[[ "$SOURCE_SHA" =~ ^[a-f0-9]{40}$ ]] || fail "invalid source SHA"
[[ "$ROLLBACK_MODE" == "manual" || "$ROLLBACK_MODE" == "code" ]] || fail "invalid rollback mode"
[[ -f "$DEPLOY_PATH/docker-compose.yml" ]] || fail "missing docker-compose.yml"
[[ -f "$DEPLOY_PATH/.env" ]] || fail "missing production .env"
[[ -f "$OVERLAY_FILE" ]] || fail "missing compose image overlay"

BASE_COMPOSE="$DEPLOY_PATH/docker-compose.yml"
ENV_FILE="$DEPLOY_PATH/.env"
DEPLOY_STATE_DIR="$DEPLOY_PATH/.deploy"
RELEASE_DIR="$DEPLOY_STATE_DIR/releases"
LOCK_DIR="$DEPLOY_STATE_DIR/lock"
STABLE_OVERLAY_FILE="$DEPLOY_STATE_DIR/compose.backend-image.yml"
ACTIVATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"
RELEASE_RECORD="$RELEASE_DIR/$ACTIVATED_AT.json"
BACKUP_FILE="$BACKUP_PATH/rabbit-$ACTIVATED_AT.sql.gz"
previous_container=""
previous_image=""
previous_image_id=""

compose_with_image() {
  local image="$1"
  shift
  BACKEND_IMAGE="$image" docker compose \
    --env-file "$ENV_FILE" \
    -f "$BASE_COMPOSE" \
    -f "$OVERLAY_FILE" \
    "$@"
}

base_compose() {
  docker compose --env-file "$ENV_FILE" -f "$BASE_COMPOSE" "$@"
}

backend_health() {
  local container_id
  container_id="$(compose_with_image "$TARGET_IMAGE" ps -q backend)"
  [[ -n "$container_id" ]] || return 1
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id"
}

wait_for_backend() {
  local health=""
  for _ in $(seq 1 60); do
    health="$(backend_health 2>/dev/null || true)"
    if [[ "$health" == "healthy" ]]; then
      return 0
    fi
    if [[ "$health" == "unhealthy" || "$health" == "exited" ]]; then
      return 1
    fi
    sleep 3
  done
  return 1
}

on_error() {
  local status=$?
  local line="${BASH_LINENO[0]:-unknown}"
  trap - ERR
  set +e
  echo "activation failed: line=$line status=$status target=$TARGET_IMAGE" >&2
  echo "backup retained: $BACKUP_FILE" >&2
  echo "previous image: ${previous_image:-none}" >&2
  if [[ "$ROLLBACK_MODE" == "code" && -n "$previous_image" ]]; then
    echo "attempting code rollback to $previous_image" >&2
    compose_with_image "$previous_image" up -d --no-deps --no-build backend
    for _ in $(seq 1 60); do
      rollback_container="$(BACKEND_IMAGE="$previous_image" docker compose --env-file "$ENV_FILE" -f "$BASE_COMPOSE" -f "$OVERLAY_FILE" ps -q backend 2>/dev/null)"
      rollback_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$rollback_container" 2>/dev/null)"
      [[ "$rollback_health" == "healthy" ]] && break
      sleep 3
    done
    echo "code rollback health: ${rollback_health:-unknown}" >&2
  else
    echo "automatic code rollback disabled; use the backup and previous image after checking migration compatibility" >&2
  fi
  exit "$status"
}
trap on_error ERR

mkdir -p "$DEPLOY_STATE_DIR" "$RELEASE_DIR" "$BACKUP_PATH"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  fail "another deployment holds $LOCK_DIR"
fi
trap 'rm -rf "$LOCK_DIR"' EXIT

install -m 600 "$OVERLAY_FILE" "$STABLE_OVERLAY_FILE"
OVERLAY_FILE="$STABLE_OVERLAY_FILE"
compose_with_image "$TARGET_IMAGE" config --quiet
mysql_container="$(base_compose ps -q mysql)"
[[ -n "$mysql_container" ]] || fail "MySQL container is not running"
mysql_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$mysql_container")"
[[ "$mysql_health" == "healthy" ]] || fail "MySQL is not healthy: $mysql_health"

previous_container="$(base_compose ps -q backend 2>/dev/null || true)"
if [[ -n "$previous_container" ]]; then
  previous_image="$(docker inspect --format '{{.Config.Image}}' "$previous_container")"
  previous_image_id="$(docker inspect --format '{{.Image}}' "$previous_container")"
fi

temporary_backup="$BACKUP_FILE.tmp"
# The variables expand inside the MySQL container, not in this script.
# shellcheck disable=SC2016
base_compose exec -T mysql sh -ec \
  'exec mysqldump --single-transaction --quick --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip -c > "$temporary_backup"
gzip -t "$temporary_backup"
[[ -s "$temporary_backup" ]]
mv "$temporary_backup" "$BACKUP_FILE"

printf '{"activatedAt":"%s","sourceSha":"%s","targetImage":"%s","previousContainer":"%s","previousImage":"%s","previousImageId":"%s","backup":"%s","status":"prepared"}\n' \
  "$ACTIVATED_AT" "$SOURCE_SHA" "$TARGET_IMAGE" "$previous_container" "$previous_image" "$previous_image_id" "$BACKUP_FILE" \
  > "$RELEASE_RECORD"

compose_with_image "$TARGET_IMAGE" pull backend
compose_with_image "$TARGET_IMAGE" up -d --no-deps --no-build backend
wait_for_backend

new_container="$(compose_with_image "$TARGET_IMAGE" ps -q backend)"
new_image_ref="$(docker inspect --format '{{.Config.Image}}' "$new_container")"
new_image_id="$(docker inspect --format '{{.Image}}' "$new_container")"
[[ "$new_image_ref" == "$TARGET_IMAGE" ]] || fail "running image ref does not match requested digest"

if [[ -n "$PUBLIC_URL" ]]; then
  response_file="$(mktemp)"
  http_status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' --connect-timeout 5 --max-time 20 "${PUBLIC_URL%/}/api/houses")"
  [[ "$http_status" == "200" || "$http_status" == "401" ]] || fail "public unauthenticated probe returned HTTP $http_status"
  grep -Eq '"code"[[:space:]]*:[[:space:]]*401' "$response_file" || fail "public probe did not return business code 401"
  rm -f "$response_file"
else
  http_status="skipped"
fi

flyway_version="$(
  # shellcheck disable=SC2016
  base_compose exec -T mysql sh -ec \
  'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"' \
  | tr -d '\r'
)"
[[ -n "$flyway_version" ]] || fail "Flyway version could not be read"

cache_status="disabled"
if [[ -n "$(base_compose ps -q valkey 2>/dev/null || true)" ]]; then
  cache_status="$(base_compose exec -T valkey valkey-cli ping | tr -d '\r')"
elif [[ -n "$(base_compose ps -q redis 2>/dev/null || true)" ]]; then
  cache_status="$(base_compose exec -T redis redis-cli ping | tr -d '\r')"
fi

printf '{"activatedAt":"%s","sourceSha":"%s","targetImage":"%s","previousContainer":"%s","previousImage":"%s","previousImageId":"%s","container":"%s","imageId":"%s","backup":"%s","flyway":"%s","cache":"%s","http":"%s","status":"healthy"}\n' \
  "$ACTIVATED_AT" "$SOURCE_SHA" "$TARGET_IMAGE" "$previous_container" "$previous_image" "$previous_image_id" \
  "$new_container" "$new_image_id" "$BACKUP_FILE" "$flyway_version" "$cache_status" "$http_status" \
  > "$RELEASE_RECORD"

printf 'deployment healthy\n'
printf 'source_sha=%s\n' "$SOURCE_SHA"
printf 'target_image=%s\n' "$TARGET_IMAGE"
printf 'container_id=%s\n' "$new_container"
printf 'image_id=%s\n' "$new_image_id"
printf 'previous_container_id=%s\n' "${previous_container:-none}"
printf 'previous_image=%s\n' "${previous_image:-none}"
printf 'backup=%s\n' "$BACKUP_FILE"
printf 'flyway=%s\n' "$flyway_version"
printf 'cache=%s\n' "$cache_status"
printf 'public_http=%s\n' "$http_status"
