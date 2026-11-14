#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_NAME="${NETWORK_NAME:-keycloak-network}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-keycloak-postgres}"
KEYCLOAK_CONTAINER_NAME="${KEYCLOAK_CONTAINER_NAME:-keycloak}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:26.4.5}"
POSTGRES_DB="${POSTGRES_DB:-keycloak}"
POSTGRES_USER="${POSTGRES_USER:-keycloak}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-admin}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-master}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
KEYCLOAK_USER_USERNAME="${KEYCLOAK_USER_USERNAME:-user}"
KEYCLOAK_USER_PASSWORD="${KEYCLOAK_USER_PASSWORD:-userpass}"
KEYCLOAK_TEST_ADMIN_USERNAME="${KEYCLOAK_TEST_ADMIN_USERNAME:-admin}"
KEYCLOAK_TEST_ADMIN_PASSWORD="${KEYCLOAK_TEST_ADMIN_PASSWORD:-adminpass}"
KEYCLOAK_NOTUSER_USERNAME="${KEYCLOAK_NOTUSER_USERNAME:-notuser}"
KEYCLOAK_NOTUSER_PASSWORD="${KEYCLOAK_NOTUSER_PASSWORD:-notuserpass}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-authtest}"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-8080}"
KEYCLOAK_MANAGEMENT_PORT="${KEYCLOAK_MANAGEMENT_PORT:-9000}"
KEYCLOAK_TEST_ADMIN_PASSWORD="${KEYCLOAK_TEST_ADMIN_PASSWORD:-adminpass}"
KEYCLOAK_NOTUSER_PASSWORD="${KEYCLOAK_NOTUSER_PASSWORD:-notuserpass}"

: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD before running this script}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD before running this script}"
: "${KEYCLOAK_USER_PASSWORD:?Set KEYCLOAK_USER_PASSWORD before running this script}"

if [[ "$KEYCLOAK_USER_USERNAME" == "$KEYCLOAK_TEST_ADMIN_USERNAME" \
  || "$KEYCLOAK_USER_USERNAME" == "$KEYCLOAK_NOTUSER_USERNAME" \
  || "$KEYCLOAK_TEST_ADMIN_USERNAME" == "$KEYCLOAK_NOTUSER_USERNAME" ]]; then
  echo "The Keycloak test users must have different usernames." >&2
  exit 1
fi

for command in docker curl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command not found: $command" >&2
    exit 1
  fi
done

docker info >/dev/null

for container_name in "$POSTGRES_CONTAINER_NAME" "$KEYCLOAK_CONTAINER_NAME"; do
  if docker container inspect "$container_name" >/dev/null 2>&1; then
    echo "Container '$container_name' already exists; remove or rename it before running setup." >&2
    exit 1
  fi
done

if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
  docker network create "$NETWORK_NAME" >/dev/null
fi

docker run --detach \
  --name "$POSTGRES_CONTAINER_NAME" \
  --network "$NETWORK_NAME" \
  --restart unless-stopped \
  --health-cmd 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  --health-interval 5s \
  --health-timeout 5s \
  --health-retries 20 \
  --health-start-period 10s \
  --volume "${POSTGRES_CONTAINER_NAME}_data:/var/lib/postgresql/data" \
  --env POSTGRES_DB="$POSTGRES_DB" \
  --env POSTGRES_USER="$POSTGRES_USER" \
  --env POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  "$POSTGRES_IMAGE" >/dev/null

echo "Waiting for PostgreSQL..."
for ((attempt = 0; attempt < 60; attempt++)); do
  health_status="$(docker inspect --format '{{.State.Health.Status}}' "$POSTGRES_CONTAINER_NAME")"
  if [[ "$health_status" == "healthy" ]]; then
    break
  fi
  if [[ "$health_status" == "unhealthy" ]]; then
    docker logs "$POSTGRES_CONTAINER_NAME" >&2
    echo "PostgreSQL did not become healthy." >&2
    exit 1
  fi
  sleep 2
done

if [[ "$health_status" != "healthy" ]]; then
  docker logs "$POSTGRES_CONTAINER_NAME" >&2
  echo "Timed out waiting for PostgreSQL." >&2
  exit 1
fi

docker exec -i "$POSTGRES_CONTAINER_NAME" \
  psql --set ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  < "$SCRIPT_DIR/database/base-objects.sql"

docker run --detach \
  --name "$KEYCLOAK_CONTAINER_NAME" \
  --network "$NETWORK_NAME" \
  --restart unless-stopped \
  --publish "${KEYCLOAK_PORT}:8080" \
  --publish "${KEYCLOAK_MANAGEMENT_PORT}:9000" \
  --volume "${KEYCLOAK_CONTAINER_NAME}_data:/opt/keycloak/data" \
  --env KC_DB=postgres \
  --env KC_DB_URL="jdbc:postgresql://${POSTGRES_CONTAINER_NAME}:5432/${POSTGRES_DB}" \
  --env KC_DB_USERNAME="$POSTGRES_USER" \
  --env KC_DB_PASSWORD="$POSTGRES_PASSWORD" \
  --env KC_HEALTH_ENABLED=true \
  --env KC_BOOTSTRAP_ADMIN_USERNAME="$KEYCLOAK_ADMIN_USERNAME" \
  --env KC_BOOTSTRAP_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" \
  "$KEYCLOAK_IMAGE" \
  start-dev --http-port=8080 >/dev/null

echo "Waiting for Keycloak..."
for ((attempt = 0; attempt < 90; attempt++)); do
  if curl --fail --silent \
    "http://127.0.0.1:${KEYCLOAK_MANAGEMENT_PORT}/health/ready" >/dev/null; then
    break
  fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$KEYCLOAK_CONTAINER_NAME")" != "true" ]]; then
    docker logs "$KEYCLOAK_CONTAINER_NAME" >&2
    echo "Keycloak stopped before becoming ready." >&2
    exit 1
  fi
  sleep 2
done

if ! curl --fail --silent \
  "http://127.0.0.1:${KEYCLOAK_MANAGEMENT_PORT}/health/ready" >/dev/null; then
  docker logs "$KEYCLOAK_CONTAINER_NAME" >&2
  echo "Timed out waiting for Keycloak." >&2
  exit 1
fi

docker exec "$KEYCLOAK_CONTAINER_NAME" \
  /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

docker exec "$KEYCLOAK_CONTAINER_NAME" \
  /opt/keycloak/bin/kcadm.sh create realms \
  -s "realm=${KEYCLOAK_REALM}" \
  -s enabled=true >/dev/null

for role in user admin objectrole; do
  docker exec "$KEYCLOAK_CONTAINER_NAME" \
    /opt/keycloak/bin/kcadm.sh create roles \
    -r "$KEYCLOAK_REALM" \
    -s "name=${role}" >/dev/null
done

create_test_user() {
  local username="$1"
  local password="$2"
  local email="$3"
  local first_name="$4"
  local last_name="$5"
  shift 5
  local roles=("$@")

  docker exec "$KEYCLOAK_CONTAINER_NAME" \
    /opt/keycloak/bin/kcadm.sh create users \
    -r "$KEYCLOAK_REALM" \
    -s "username=${username}" \
    -s "email=${email}" \
    -s "firstName=${first_name}" \
    -s "lastName=${last_name}" \
    -s emailVerified=true \
    -s enabled=true >/dev/null

  docker exec "$KEYCLOAK_CONTAINER_NAME" \
    /opt/keycloak/bin/kcadm.sh set-password \
    -r "$KEYCLOAK_REALM" \
    --username "$username" \
    --new-password "$password" >/dev/null

  for role in "${roles[@]}"; do
    docker exec "$KEYCLOAK_CONTAINER_NAME" \
      /opt/keycloak/bin/kcadm.sh add-roles \
      -r "$KEYCLOAK_REALM" \
      --uusername "$username" \
      --rolename "$role" >/dev/null
  done
}

create_test_user "$KEYCLOAK_USER_USERNAME" "$KEYCLOAK_USER_PASSWORD" \
  user@example.test Test User user objectrole
create_test_user "$KEYCLOAK_TEST_ADMIN_USERNAME" "$KEYCLOAK_TEST_ADMIN_PASSWORD" \
  admin@example.test Test Admin admin objectrole
create_test_user "$KEYCLOAK_NOTUSER_USERNAME" "$KEYCLOAK_NOTUSER_PASSWORD" \
  notuser@example.test Test "No Roles"

notuser_id="$(docker exec "$KEYCLOAK_CONTAINER_NAME" \
  /opt/keycloak/bin/kcadm.sh get users \
  -r "$KEYCLOAK_REALM" \
  -q "username=$KEYCLOAK_NOTUSER_USERNAME" \
  -q exact=true \
  --fields id \
  --format csv \
  --noquotes)"
notuser_realm_roles="$(docker exec "$KEYCLOAK_CONTAINER_NAME" \
  /opt/keycloak/bin/kcadm.sh get "users/$notuser_id/role-mappings/realm" \
  -r "$KEYCLOAK_REALM" \
  --fields name \
  --format csv \
  --noquotes)"
default_realm_role="default-roles-$KEYCLOAK_REALM"
case $'\n'"$notuser_realm_roles"$'\n' in
  *$'\n'"$default_realm_role"$'\n'*)
    docker exec "$KEYCLOAK_CONTAINER_NAME" \
      /opt/keycloak/bin/kcadm.sh remove-roles \
      -r "$KEYCLOAK_REALM" \
      --uid "$notuser_id" \
      --rolename "$default_realm_role" >/dev/null
    ;;
esac

printf 'Keycloak is ready at http://localhost:%s\n' "$KEYCLOAK_PORT"
printf 'Admin user: %s (realm: master)\n' "$KEYCLOAK_ADMIN_USERNAME"
printf 'Test user: %s (roles: user, objectrole; realm: %s)\n' "$KEYCLOAK_USER_USERNAME" "$KEYCLOAK_REALM"
printf 'Test admin: %s (roles: admin, objectrole; realm: %s)\n' \
  "$KEYCLOAK_TEST_ADMIN_USERNAME" "$KEYCLOAK_REALM"
printf 'Roleless test user: %s (no application roles; realm: %s)\n' \
  "$KEYCLOAK_NOTUSER_USERNAME" "$KEYCLOAK_REALM"
printf 'PostgreSQL container: %s (database: %s, user: %s)\n' \
  "$POSTGRES_CONTAINER_NAME" "$POSTGRES_DB" "$POSTGRES_USER"
