#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 scripts/init-env.py
docker compose up --build --wait --wait-timeout 300
python3 scripts/smoke-test.py
