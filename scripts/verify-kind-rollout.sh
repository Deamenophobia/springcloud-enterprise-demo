#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

cluster=spring-rollout-lab
context="kind-$cluster"
namespace=commerce-demo
clusters=$(kind get clusters)
if grep -Fxq "$cluster" <<< "$clusters"; then
  echo "A kind cluster named $cluster already exists" >&2
  exit 2
fi

KUBECONFIG=$(mktemp)
export KUBECONFIG
forward_log=$(mktemp)
forward_pid=
cleanup() {
  if [[ -n "$forward_pid" ]]; then
    kill "$forward_pid" 2>/dev/null || true
    wait "$forward_pid" 2>/dev/null || true
  fi
  kind delete cluster --name "$cluster" || true
  rm -f -- "$KUBECONFIG" "$forward_log"
}
trap cleanup EXIT

kind create cluster --name "$cluster" --wait 120s
kind load docker-image --name "$cluster" commerce/gateway:dev commerce/catalog-service:dev commerce/order-service:dev
bash scripts/deploy.sh demo "$context" commerce dev .env

kube=(kubectl --context "$context" -n "$namespace")
revision=$("${kube[@]}" get deployment/order-service -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}')
healthy_image=$("${kube[@]}" get deployment/order-service -o jsonpath='{.spec.template.spec.containers[0].image}')

"${kube[@]}" port-forward --address 127.0.0.1 service/gateway 18080:8080 > "$forward_log" 2>&1 &
forward_pid=$!
for _ in {1..20}; do
  if grep -q 'Forwarding from 127.0.0.1:18080' "$forward_log"; then break; fi
  if ! kill -0 "$forward_pid" 2>/dev/null; then cat "$forward_log" >&2; exit 1; fi
  sleep 1
done
grep -q 'Forwarding from 127.0.0.1:18080' "$forward_log" || { cat "$forward_log" >&2; exit 1; }
export BASE_URL=http://127.0.0.1:18080
python3 scripts/smoke-test.py

"${kube[@]}" set image deployment/order-service app=commerce/order-service:lab-missing
if "${kube[@]}" rollout status deployment/order-service --timeout=30s; then
  echo 'Expected the candidate image to fail to pull' >&2
  exit 1
fi
states=$("${kube[@]}" get pods -l app=order-service \
  -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[0].state.waiting.reason}{"\n"}{end}')
printf '%s\n' "$states"
grep -Eq 'ErrImagePull|ImagePullBackOff' <<< "$states"

bash scripts/rollback.sh "$context" "$namespace" order-service "$revision"
restored_image=$("${kube[@]}" get deployment/order-service -o jsonpath='{.spec.template.spec.containers[0].image}')
[[ "$restored_image" == "$healthy_image" ]]
python3 scripts/smoke-test.py
printf 'PASS: observed failed image pull, restored revision %s (%s), verified gateway API\n' "$revision" "$restored_image"
