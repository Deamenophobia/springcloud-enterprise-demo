#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
registry="${1:?Usage: build-images.sh REGISTRY TAG [--push]}"
tag="${2:?An immutable image tag is required}"
[[ "$registry" =~ ^[a-zA-Z0-9][a-zA-Z0-9./:_-]*$ && "$tag" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo 'Invalid registry or tag' >&2; exit 2; }
mvn -B -ntp verify
for service in gateway catalog-service order-service; do
  docker build --build-arg "SERVICE=$service" -t "$registry/$service:$tag" .
  if [[ "${3:-}" == "--push" ]]; then docker push "$registry/$service:$tag"; fi
done
