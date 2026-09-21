# Verification record

Verified on 2026-09-22 using Windows, Java 21.0.5, Maven 3.9.9 and portable PostgreSQL 17.6.

## Passed

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

## Execution details

The regular test suite was run with Maven verify. The updated order-service suite was then rerun with -pl order-service -am verify after correcting circuit-breaker exception handling. The PostgreSQL profile was run with -Ppostgres-it -pl order-service -am verify using a dedicated orders_it database.

Maven used an external local dependency cache. The temporary PostgreSQL fixture and local runtime harness were kept outside the repository and are not needed to run the documented test commands. Its Java service processes and the temporary PostgreSQL server were stopped after verification.

The integration profile creates a unique test schema and drops only that schema. The full-stack run used local throwaway catalog/orders databases and ports 18080-18082 (application) and 19080-19082 (management). These are verification ports; the documented Compose gateway port is 8080.

## Not executed here

- Docker image builds and container runtime checks: Docker Engine was not running.
- Kubernetes apply, image pulls, PVC provisioning, ingress, HPA behavior and rollback: no cluster/context was configured.
- The GitHub Actions workflow: supplied, but not run on a remote repository.
- Production identity-provider integration, database TLS/CA mounting, capacity/load testing and operational recovery: environment-specific work.

The delivered CI workflow is configured to run Java/PostgreSQL checks and the Docker smoke test on a Linux runner. Prepared Kubernetes manifests and successful local verification are not evidence of a deployment to a real cluster.
