#!/usr/bin/env python3
"""Local HS256 demo token; production tokens must come from your identity provider."""
import argparse
import base64
import hashlib
import hmac
import json
import os
import pathlib
import time

def settings():
    values = {}
    path = pathlib.Path(__file__).resolve().parents[1] / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
    values.update(os.environ)
    return values

def token(subject="demo-user", scope="catalog.read orders.read orders.write", expires_in=900):
    env = settings()
    if env.get("JWT_MODE") != "demo":
        raise SystemExit("Token minting requires JWT_MODE=demo; use your identity provider for OIDC.")
    key = base64.b64decode(env["JWT_SECRET"], validate=True)
    if len(key) < 32:
        raise SystemExit("JWT_SECRET must contain at least 32 random bytes")
    def enc(value):
        return base64.urlsafe_b64encode(value).decode().rstrip("=")
    now = int(time.time())
    claims = {"sub": subject, "iss": env.get("JWT_ISSUER", "commerce-demo"),
              "aud": [env.get("JWT_AUDIENCE", "commerce-api")], "iat": now,
              "exp": now + expires_in, "scope": scope}
    body = enc(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()) + "." + enc(json.dumps(claims).encode())
    return body + "." + enc(hmac.new(key, body.encode(), hashlib.sha256).digest())

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--subject", default="demo-user")
    parser.add_argument("--scope", default="catalog.read orders.read orders.write")
    args = parser.parse_args()
    print(token(args.subject, args.scope))
