#!/usr/bin/env python3
"""Exercise the complete demo through its gateway. No third-party Python packages."""
import concurrent.futures
import importlib.util
import json
import os
import urllib.error
import urllib.request
import uuid
from pathlib import Path
spec = importlib.util.spec_from_file_location("demo_token", Path(__file__).with_name("demo-token.py"))
auth = importlib.util.module_from_spec(spec)
spec.loader.exec_module(auth)
base = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
bearer = auth.token()
def call(method, path, data=None, token=bearer, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(),
                                 headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else {}, response.headers
def expect(status, result):
    assert result[0] == status, (status, result)
    return result
expect(401, call("GET", "/api/products", token=None))
expect(401, call("GET", "/api/products", token=auth.token(expires_in=-3600)))
expect(403, call("GET", "/api/products", token=auth.token(scope="orders.read")))
expect(200, call("GET", "/api/products"))
expect(400, call("GET", "/api/products?limit=101"))
data = {"sku": "KEYBOARD-001", "quantity": 2}
expect(403, call("POST", "/api/orders", data, token=auth.token(scope="orders.write"), key=str(uuid.uuid4())))
key = str(uuid.uuid4())
first = expect(201, call("POST", "/api/orders", data, key=key))
assert first[1]["total"] == 178
second = expect(200, call("POST", "/api/orders", data, key=key))
assert second[1]["id"] == first[1]["id"] and second[2]["Idempotency-Replayed"] == "true"
expect(409, call("POST", "/api/orders", {**data, "quantity": 3}, key=key))
expect(400, call("POST", "/api/orders", {**data, "quantity": 0}, key=str(uuid.uuid4())))
expect(400, call("POST", "/api/orders", data))
expect(422, call("POST", "/api/orders", {**data, "sku": "UNKNOWN"}, key=str(uuid.uuid4())))
path = "/api/orders/" + first[1]["id"]
expect(200, call("GET", path))
expect(404, call("GET", path, token=auth.token("another-user")))
key = str(uuid.uuid4())
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(lambda _: call("POST", "/api/orders", data, key=key), range(8)))
assert sum(r[0] == 201 for r in results) == 1, results
assert all(r[0] in (200, 201) for r in results), results
assert len({r[1]["id"] for r in results}) == 1, results
print("PASS: authentication, scopes, pagination, order totals, replay/conflict, validation, ownership and concurrent idempotency")
