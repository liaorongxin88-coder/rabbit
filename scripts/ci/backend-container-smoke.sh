#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
IMAGE="${1:-rabbit-backend:harness}"
RUN_SUFFIX="${GITHUB_RUN_ID:-$$}"
NETWORK="rabbit-ci-$RUN_SUFFIX"
MYSQL_CONTAINER="rabbit-ci-mysql-$RUN_SUFFIX"
BACKEND_CONTAINER="rabbit-ci-backend-$RUN_SUFFIX"
RESPONSE_FILE="$(mktemp)"

cleanup() {
  local status=$?
  set +e
  if [[ $status -ne 0 ]]; then
    docker logs "$BACKEND_CONTAINER" 2>/dev/null || true
    docker logs "$MYSQL_CONTAINER" 2>/dev/null || true
  fi
  docker rm --force "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
  docker rm --force "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -f "$RESPONSE_FILE"
  exit "$status"
}
trap cleanup EXIT

cd "$ROOT_DIR"

docker image inspect "$IMAGE" >/dev/null
docker network create "$NETWORK" >/dev/null
docker run --detach \
  --name "$MYSQL_CONTAINER" \
  --network "$NETWORK" \
  --env MYSQL_ROOT_PASSWORD=rabbit_root \
  --env MYSQL_DATABASE=rabbit_app \
  --env TZ=Asia/Shanghai \
  --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -prabbit_root' \
  --health-interval=2s \
  --health-timeout=3s \
  --health-retries=30 \
  mysql:8.0 >/dev/null

for _ in $(seq 1 60); do
  if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER")" == "healthy" ]]; then
    break
  fi
  sleep 1
done
[[ "$(docker inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER")" == "healthy" ]]

docker run --detach \
  --name "$BACKEND_CONTAINER" \
  --network "$NETWORK" \
  --publish 127.0.0.1::8080 \
  --env SPRING_DATASOURCE_URL="jdbc:mysql://$MYSQL_CONTAINER:3306/rabbit_app?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --env SPRING_DATASOURCE_USERNAME=root \
  --env SPRING_DATASOURCE_PASSWORD=rabbit_root \
  --env APP_JWT_SECRET=ci-only-app-jwt-secret-0123456789abcdef \
  --env APP_ADMIN_JWT_SECRET=ci-only-admin-jwt-secret-fedcba9876543210 \
  --env APP_PHONE_HASH_SECRET=ci-only-phone-hash-secret-0123456789abcdef \
  --env APP_ADMIN_BOOTSTRAP_ENABLED=false \
  --env APP_SMS_ENABLED=false \
  --env APP_NFC_TAG_ACTIVE_KEY_ID=1 \
  --env APP_NFC_TAG_SIGNING_KEYS=1=Y2ktb25seS1uZmMtc2lnbmluZy1rZXktMDEyMzQ1Njc4OWFiY2RlZg== \
  --env TZ=Asia/Shanghai \
  "$IMAGE" >/dev/null

for _ in $(seq 1 90); do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$BACKEND_CONTAINER")"
  if [[ "$health" == "healthy" ]]; then
    break
  fi
  if [[ "$health" == "exited" || "$health" == "unhealthy" ]]; then
    exit 1
  fi
  sleep 2
done
[[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$BACKEND_CONTAINER")" == "healthy" ]]

host_port="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' "$BACKEND_CONTAINER")"
http_status="$(curl --silent --show-error --output "$RESPONSE_FILE" --write-out '%{http_code}' "http://127.0.0.1:$host_port/api/houses")"
[[ "$http_status" == "200" || "$http_status" == "401" ]]
grep -Eq '"code"[[:space:]]*:[[:space:]]*401' "$RESPONSE_FILE"

image_id="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
container_id="$(docker inspect --format '{{.Id}}' "$BACKEND_CONTAINER")"
printf 'container smoke passed: image=%s container=%s http=%s business_code=401\n' "$image_id" "$container_id" "$http_status"
