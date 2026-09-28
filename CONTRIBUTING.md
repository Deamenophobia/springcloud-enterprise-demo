# Contributing

Use Java 21 and Maven 3.9+ for local development. Docker Compose and Python 3 run the full demo.

1. Create a branch for the change.
2. Run `mvn -B -ntp verify`.
3. Run the PostgreSQL integration profile and Docker smoke test when changing database behavior, API contracts or deployment files. See [the README](README.md).
4. Stage the intended source files and run `python3 scripts/check-publication.py`.
5. Open a pull request describing the behavior change and validation.

Keep URLs and filesystem paths portable. Use repository-relative paths in scripts and environment variables for service endpoints. Loopback URLs are appropriate for tests, container health checks and local examples; Docker and Kubernetes use service DNS for inter-service traffic.

Do not commit local environment files, signing keys, kubeconfigs, rendered manifests, database files, logs or build outputs. Environment examples contain placeholders only. The checked-in JWT keys and CI database password are disposable test fixtures, never deployment credentials.

A new Flyway migration must preserve compatibility with the previous application release during rolling deployments. Include idempotency and ownership checks when changing order behavior.

Teaching experiments belong in `framework-labs`, with article drafts under `docs/learning`. Assert observable behavior, include a counterexample where useful, and describe the limits of each test. Use `clean test` before producing evidence with `python3 scripts/lab-report.py`; also run `python3 -m unittest discover -s scripts/tests -v` when changing the reporter. Keep raw build output and screenshots containing local paths or credentials out of Git. The generated CI artifact provides sanitized named test results.
