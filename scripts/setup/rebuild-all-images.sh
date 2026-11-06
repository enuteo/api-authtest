#!/usr/bin/env bash
set -Eeuo pipefail

TCC_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
BUILD_SCRIPT="$TCC_ROOT/build-images.sh"
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

if [[ ! -x "$BUILD_SCRIPT" ]]; then
  echo "Image build script not found or not executable: $BUILD_SCRIPT" >&2
  exit 1
fi

for service in "${SERVICES[@]}"; do
  if [[ ! -f "$TCC_ROOT/$service/Dockerfile" ]]; then
    echo "Dockerfile not found for service: $service" >&2
    exit 1
  fi
done

docker info >/dev/null

for service in "${SERVICES[@]}"; do
  image="tcc/${service}:${IMAGE_TAG}"
  if docker image inspect "$image" >/dev/null 2>&1; then
    printf 'Removing %s\n' "$image"
    docker image rm "$image"
  else
    printf 'Image not found, skipping: %s\n' "$image"
  fi
done

IMAGE_TAG="$IMAGE_TAG" "$BUILD_SCRIPT"
