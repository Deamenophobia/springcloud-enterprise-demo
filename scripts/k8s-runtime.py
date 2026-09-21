#!/usr/bin/env python3
"""Validate runtime settings; emit Kubernetes objects only to a kubectl pipe."""
import base64
import json
import os
import sys
mode, namespace = sys.argv[1:]
env = os.environ
required = ["JWT_MODE", "JWT_ISSUER", "JWT_AUDIENCE", "CATALOG_DB_URL", "CATALOG_DB_USER",
            "CATALOG_DB_PASSWORD", "ORDER_DB_URL", "ORDER_DB_USER", "ORDER_DB_PASSWORD"]
for key in required:
    if not env.get(key) or "REPLACE_ME" in env[key] or "example.com" in env[key]:
        raise SystemExit("Set a real value for " + key)
if mode == "production":
    if env["JWT_MODE"] != "oidc":
        raise SystemExit("Production requires JWT_MODE=oidc")
    if not env["JWT_ISSUER"].startswith("https://") or not env.get("JWT_JWK_SET_URI", "").startswith("https://"):
        raise SystemExit("Production requires HTTPS issuer and JWK URLs")
    if any("sslmode=verify-full" not in env[k] for k in ["CATALOG_DB_URL", "ORDER_DB_URL"]):
        raise SystemExit("Production PostgreSQL URLs must include sslmode=verify-full")
elif mode == "demo":
    if env["JWT_MODE"] != "demo" or not env.get("JWT_SECRET"):
        raise SystemExit("Demo requires JWT_MODE=demo and JWT_SECRET")
else:
    raise SystemExit("Use demo or production")
items = []
def add(kind, name, values):
    items.append({"apiVersion": "v1", "kind": kind, "metadata": {"name": name, "namespace": namespace},
                  "data": {k: base64.b64encode(v.encode()).decode() for k,v in values.items()} if kind == "Secret" else values})
add("ConfigMap", "runtime-config", {
    "JWT_MODE": env["JWT_MODE"], "JWT_ISSUER": env["JWT_ISSUER"],
    "JWT_AUDIENCE": env["JWT_AUDIENCE"], "JWT_JWK_SET_URI": env.get("JWT_JWK_SET_URI", ""),
    "CATALOG_URL": "http://catalog-service:8080", "ORDER_URL": "http://order-service:8080"})
add("Secret", "auth-secret", {"JWT_SECRET": env.get("JWT_SECRET", "") if mode == "demo" else ""})
for service, prefix in [("catalog-service", "CATALOG"), ("order-service", "ORDER")]:
    add("Secret", service + "-db", {k: env[prefix + "_" + k] for k in ["DB_URL", "DB_USER", "DB_PASSWORD"]})
print(json.dumps({"apiVersion": "v1", "kind": "List", "items": items}))
