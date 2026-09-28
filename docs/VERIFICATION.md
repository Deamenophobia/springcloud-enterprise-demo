# Verification record

## Framework laboratory addition — 2026-09-28

- A clean Maven reactor build passed **39 Java tests**, with zero failures, errors or skips: the original 23 application tests plus 16 new laboratory experiments.
- The laboratory verifies seven container scenarios, three Boot/JDBC auto-configuration scenarios, and six real transaction scenarios using isolated H2 databases.
- All three service JARs packaged successfully. The laboratory has test-scoped dependencies only and is not a dependency of any service.
- Five Python reporter tests passed, including negative checks for missing/empty/inconsistent reports, failed or skipped tests, and exclusion of private properties/logs.
- Local Markdown link targets and Git whitespace checks passed.

This run used Windows, Java 21.0.5 and Maven 3.9.9, with Spring Framework 7.0.9 and H2 2.4.240 resolved by the existing Boot parent. Maven used an external dependency cache and an ASCII-only JVM temporary directory to avoid a Windows Surefire launcher problem. No local paths were added to the build configuration.

Commands: `mvn -B -ntp clean verify`, `python -m unittest discover -s scripts/tests -v`, then `python scripts/lab-report.py`. This local run did not enable the PostgreSQL profile or start Docker or Kubernetes. The updated CI workflow runs PostgreSQL and Docker checks and preserves sanitized laboratory evidence. See [the article-series guide](learning/README.md) for commands and evidence limits.

## Published baseline CI

Before the laboratory addition, [GitHub Actions run 35638746768](https://github.com/Deamenophobia/springcloud-enterprise-demo/actions/runs/35638746768) passed at commit `1a2a1edbb6f8643fc7c70f44eaba3ae5ac3a1891`. That run executed Java/PostgreSQL checks, Docker image builds, Compose startup and API smoke tests. It supersedes the original machine's Docker and remote-CI limitations below, but does not establish a live Kubernetes rollout.

## Original local verification — 2026-09-22

The following historical record used Windows, Java 21.0.5, Maven 3.9.9 and portable PostgreSQL 17.6.

### Passed

- All four Maven modules compile and all three executable Spring Boot service JARs package.
- **23 Java tests:** JWT signatures/issuer/audience/expiry/subject/key strength, gateway startup and authentication, controller authorization and validation, owner-scoped reads, server-side pricing, idempotency, and real HTTP/circuit-breaker error mapping.
- **1 PostgreSQL integration test:** real Flyway migration and JDBC record mapping; eight concurrent requests result in one created order and one shared order ID; another customer cannot read it.
- Packaged JAR inspection confirms the current error-mapping classes and configuration are present. The build forces fresh JAR creation before Boot repackaging.
- All three packaged JARs started against PostgreSQL with separate catalog and orders databases and reported ready.
- The gateway smoke test passed: missing/expired tokens, insufficient scopes, product pagination, trusted order totals, created/replayed status, changed-payload conflicts, invalid/missing inputs, unknown SKU handling, customer isolation and eight concurrent requests.
- With the catalog process stopped, a new order returned 503 and an existing order replay returned 200 with the original ID.
- Docker Compose configuration validation passed.
- Both Kustomize overlays rendered 17 objects; application workload security, probe and resource settings were checked.
- Bash syntax, Python syntax, YAML parsing and duplicate YAML key checks passed.
- The Kubernetes runtime helper emitted the expected ConfigMap and three base64-encoded Secrets; production mode rejected demo authentication.

### Execution details

The regular test suite was run with Maven verify. The updated order-service suite was then rerun with -pl order-service -am verify after correcting circuit-breaker exception handling. The PostgreSQL profile was run with -Ppostgres-it -pl order-service -am verify using a dedicated orders_it database.

Maven used an external local dependency cache. The temporary PostgreSQL fixture and local runtime harness were kept outside the repository and are not needed to run the documented test commands. Its Java service processes and the temporary PostgreSQL server were stopped after verification.

The integration profile creates a unique test schema and drops only that schema. The full-stack run used local throwaway catalog/orders databases and ports 18080-18082 (application) and 19080-19082 (management). These are verification ports; the documented Compose gateway port is 8080.

### Not executed on the original local machine

- Docker image builds and container runtime checks: Docker Engine was not running.
- Kubernetes apply, image pulls, PVC provisioning, ingress, HPA behavior and rollback: no cluster/context was configured.
- The GitHub Actions workflow: supplied, but not run on a remote repository.
- Production identity-provider integration, database TLS/CA mounting, capacity/load testing and operational recovery: environment-specific work.

The delivered CI workflow is configured to run Java/PostgreSQL checks and the Docker smoke test on a Linux runner. Prepared Kubernetes manifests and successful local verification are not evidence of a deployment to a real cluster.
