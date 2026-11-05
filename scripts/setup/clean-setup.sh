#!/usr/bin/env bash
set -Eeuo pipefail

NETWORK_NAME="${NETWORK_NAME:-keycloak-network}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-keycloak-postgres}"
KEYCLOAK_CONTAINER_NAME="${KEYCLOAK_CONTAINER_NAME:-keycloak}"

if [[ $# -gt 1 ]] || { [[ $# -eq 1 ]] && [[ "$1" != "--yes" ]]; }; then
  echo "Usage: $0 [--yes]" >&2
  exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Required command not found: docker" >&2
  exit 1
fi

docker info >/dev/null

echo "This removes the setup containers and persistent data volumes permanently:"
printf '  Containers: base-mysql, %s, %s\n' \
  "$POSTGRES_CONTAINER_NAME" "$KEYCLOAK_CONTAINER_NAME"
printf '  Volumes:    %s_data, %s_data\n' \
  "$POSTGRES_CONTAINER_NAME" "$KEYCLOAK_CONTAINER_NAME"
printf '  Network:    %s\n' "$NETWORK_NAME"
if [[ "${1:-}" != "--yes" ]]; then
  if [[ ! -t 0 ]]; then
    echo "Confirmation required; rerun interactively or pass --yes." >&2
    exit 1
  fi
  read -r -p "Continue? [y/N] " confirmation
  if [[ "$confirmation" != "y" && "$confirmation" != "Y" ]]; then
    echo "Cleanup cancelled."
    exit 0
  fi
fi

for container_name in base-mysql "$KEYCLOAK_CONTAINER_NAME" "$POSTGRES_CONTAINER_NAME"; do
  if docker container inspect "$container_name" >/dev/null 2>&1; then
    docker container rm --force --volumes "$container_name"
  else
    printf "Container not found, skipping: %s\n" "$container_name"
  fi
done

for volume_name in "${KEYCLOAK_CONTAINER_NAME}_data" "${POSTGRES_CONTAINER_NAME}_data"; do
  if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    docker volume rm "$volume_name"
  else
    printf "Volume not found, skipping: %s\n" "$volume_name"
  fi
done

if docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
  docker network rm "$NETWORK_NAME"
else
  printf "Network not found, skipping: %s\n" "$NETWORK_NAME"
fi

echo "Setup containers and persistent volumes removed."