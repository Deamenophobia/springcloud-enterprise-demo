#!/usr/bin/env python3
"""Check the Git index for private paths, credential files and common secret formats.

Checks staged bytes, not the working tree, so an old staged secret cannot be hidden
by a later unstaged edit. This is a focused hygiene check, not a complete secret scanner.
"""
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCKED_DIRECTORIES = {
    "target", ".m2", ".secrets", ".rendered", ".idea", ".vscode",
    "__pycache__", ".venv", "venv", ".kube",
}
BLOCKED_SUFFIXES = {
    ".log", ".jar", ".war", ".class", ".pyc", ".pyo", ".hprof",
    ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore",
    ".db", ".sqlite", ".sqlite3", ".zip", ".tar", ".gz",
}
ENV_EXAMPLES = {".env.example", ".env.production.example"}
PRIVATE_PATH = re.compile(
    r"(?<![A-Za-z0-9])(?:[A-Za-z]:[\\/]|file:\x2f\x2f|/(?:Users|home)/[^/\s]+/)"
)
SECRET_FORMAT = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----"
    r"|\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})\b"
)


def violations(name, content):
    path = PurePosixPath(name)
    errors = []
    if any(part in BLOCKED_DIRECTORIES for part in path.parts):
        errors.append("generated or private directory")
    if path.suffix.lower() in BLOCKED_SUFFIXES:
        errors.append("generated output or credential-file extension")
    if ((path.name.startswith(".env") or path.name.endswith(".env"))
            and path.name not in ENV_EXAMPLES):
        errors.append("private environment file")
    if path.name == "kubeconfig" or path.name.startswith("kubeconfig."):
        errors.append("Kubernetes credentials")
    if len(content) > 1024 * 1024:
        errors.append("unexpected file larger than 1 MiB")
    text = content.decode("utf-8", errors="replace")
    if PRIVATE_PATH.search(text):
        errors.append("machine-specific filesystem path")
    if SECRET_FORMAT.search(text):
        errors.append("possible credential or private key")
    return errors


def main():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "-z"], cwd=ROOT,
        check=True, capture_output=True,
    )
    names = result.stdout.decode("utf-8").split("\0")
    names = [name for name in names if name]
    if not names:
        raise SystemExit("No staged/tracked files found. Stage the intended source files first.")
    failures = []
    for name in names:
        content = subprocess.run(
            ["git", "show", ":" + name], cwd=ROOT, check=True, capture_output=True,
        ).stdout
        for reason in violations(name, content):
            failures.append(name + ": " + reason)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("PASS: publication hygiene checks for " + str(len(names)) + " indexed files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
