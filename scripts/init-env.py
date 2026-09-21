#!/usr/bin/env python3
"""Create local demo credentials once; never overwrite existing credentials."""
import base64
import pathlib
import secrets
root = pathlib.Path(__file__).resolve().parents[1]
path = root / ".env"
content = (
    "JWT_MODE=demo\nJWT_ISSUER=commerce-demo\nJWT_AUDIENCE=commerce-api\n"
    "JWT_SECRET=" + base64.b64encode(secrets.token_bytes(32)).decode() + "\n"
    "CATALOG_DB_PASSWORD=" + secrets.token_hex(24) + "\n"
    "ORDER_DB_PASSWORD=" + secrets.token_hex(24) + "\n"
    "CATALOG_DB_URL=jdbc:postgresql://catalog-db:5432/catalog\nCATALOG_DB_USER=catalog\n"
    "ORDER_DB_URL=jdbc:postgresql://order-db:5432/orders\nORDER_DB_USER=orders\n"
)
try:
    with path.open("x", encoding="utf-8", newline="\n") as out:
        out.write(content)
    path.chmod(0o600)
    print("Created .env with random demo credentials. Keep it private.")
except FileExistsError:
    print(".env already exists; preserved existing credentials.")
