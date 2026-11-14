#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../../.." && pwd)"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICES=(
  api-authtest
  auth-api
  no-secure-api
  secure-api
  secure-api-gateway
)

if ! command -v docker >/dev/null 2>&1; then
  echo "Required command not found: docker" >&2
  exit 1
fi

docker info >/dev/null

for service in "${SERVICES[@]}"; do
  if [[ ! -f "$ROOT_DIR/$service/Dockerfile" ]]; then
    echo "Dockerfile not found for service: $service" >&2
    exit 1
  fi

  image="tcc/${service}:${IMAGE_TAG}"
  printf '\nBuilding %s\n' "$image"
  docker build --tag "$image" "$ROOT_DIR/$service"
done

echo
echo "All service images built with tag '$IMAGE_TAG'."
