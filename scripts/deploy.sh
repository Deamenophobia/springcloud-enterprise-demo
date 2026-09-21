#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mode="${1:?Usage: deploy.sh demo|production CONTEXT REGISTRY TAG ENV_FILE}"
context="${2:?Explicit kubectl context required}"
registry="${3:?Registry required}"
tag="${4:?Immutable tag required}"
env_file="${5:?Environment file required}"
[[ "$mode" == "demo" || "$mode" == "production" ]] || exit 2
[[ "$registry" =~ ^[a-zA-Z0-9][a-zA-Z0-9./:_-]*$ && "$tag" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || exit 2
[[ -f "$env_file" ]] || { echo 'Environment file not found' >&2; exit 2; }
namespace=commerce
[[ "$mode" == "production" ]] || namespace=commerce-demo
set -a
# shellcheck disable=SC1090
source "$env_file"
set +a
# Validate before any cluster mutation. Do not print or persist generated secret data.
python3 scripts/k8s-runtime.py "$mode" "$namespace" > /dev/null
mkdir -p .rendered
kubectl kustomize "k8s/overlays/$mode" |
  REGISTRY="$registry" IMAGE_TAG="$tag" python3 -c 'import os,sys; s=sys.stdin.read();
for name in ("gateway","catalog-service","order-service"): s=s.replace("commerce/"+name+":dev",os.environ["REGISTRY"]+"/"+name+":"+os.environ["IMAGE_TAG"])
print(s)' > ".rendered/$mode.yaml"
kubectl --context "$context" create namespace "$namespace" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
python3 scripts/k8s-runtime.py "$mode" "$namespace" | kubectl --context "$context" apply --server-side --field-manager=commerce-runtime -f -
kubectl --context "$context" apply -f ".rendered/$mode.yaml"
# Environment variables from Secrets/ConfigMaps require new pods, even when the image tag is unchanged.
kubectl --context "$context" -n "$namespace" rollout restart deployment/gateway deployment/catalog-service deployment/order-service
for service in catalog-service order-service gateway; do
  kubectl --context "$context" -n "$namespace" rollout status "deployment/$service" --timeout=300s
done
printf 'Deployed to context=%s namespace=%s\n' "$context" "$namespace"
