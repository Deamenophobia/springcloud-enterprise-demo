#!/usr/bin/env bash
set -Eeuo pipefail
context="${1:?Usage: rollback.sh CONTEXT NAMESPACE SERVICE REVISION}"
namespace="${2:?Namespace required}"
service="${3:?Service required}"
revision="${4:?Revision required}"
[[ "$service" =~ ^(gateway|catalog-service|order-service)$ && "$revision" =~ ^[1-9][0-9]*$ ]] || exit 2
kubectl --context "$context" -n "$namespace" rollout undo "deployment/$service" --to-revision="$revision"
kubectl --context "$context" -n "$namespace" rollout status "deployment/$service" --timeout=300s
