# Spring architecture through executable experiments

This series connects Spring's mechanisms to decisions in the commerce application. Start with a question, execute a small experiment, examine the assertion, and then follow the same responsibility into the service code. The existing conceptual articles **04.02 — Java and Spring Boot** and **04.03 — Spring Cloud and Distributed Services** provide the broad introduction; these chapters provide the runnable companion.

The first three chapters are repository drafts with executable evidence. Website publication is a separate editorial step. The remaining chapters below are a roadmap, not completed articles or claims of test coverage.

## Read and run

The initial experiments were verified on 2026-09-28 with Java 21, Spring Boot 4.1.1, Spring Framework 7.0.9 and H2 2.4.240. The dependency versions come from the existing parent POM; these lessons do not upgrade the application stack.

Use JDK 21 and Maven 3.9+. From the repository root:

```bash
mvn -B -ntp -pl framework-labs clean test
python3 scripts/lab-report.py
```

On PowerShell use `python` instead of `python3`. No Docker, external database or credentials are needed for these lessons. Maven downloads dependencies on the first run. An intentionally ambiguous bean logs a startup warning: the test passes by verifying that Spring rejects that configuration.

The result is **16 tests: 7 container, 3 auto-configuration, 6 transaction**. Generated Markdown and JSON are in `framework-labs/target/evidence/`; raw Surefire XML stays in `framework-labs/target/surefire-reports/`. Both directories are ignored by Git. Always run `clean test` before generating evidence; the reporter reads existing XML and cannot establish its freshness. It exits unsuccessfully for missing suites, malformed reports, zero tests, failures, errors or skipped tests.

For the complete application suite, run `mvn -B -ntp clean verify`. The additional `postgres-it` profile requires the dedicated PostgreSQL database described in [the README](../../README.md#build-and-test-the-java-code).

`framework-labs` is a test-only module. H2 and all other dependencies have test scope. None of the commerce services depends on it, and its fixtures do not enter the runtime images.

## Series and evidence map

| Chapter | Question and enterprise connection | Evidence and status |
|---|---|---|
| [1. The container](01-spring-container.md) | Who constructs collaborators, selects implementations and owns their lifecycle? | Draft complete; `BeanContainerTest`, 7 tests |
| [2. Boot and JDBC](02-boot-jdbc.md) | How do classpath and application choices produce usable infrastructure? | Draft complete; `JdbcAutoConfigurationTest`, 3 tests |
| [3. Transaction boundaries](03-transaction-boundaries.md) | Which calls are actually transactional, and what survives a failure? | Draft complete; `TransactionProxyTest`, 6 H2 tests |
| 4. HTTP contracts and security | How do routing, JSON, validation and authorization cooperate? | Planned article; existing MVC tests, `TokenPolicyTest` and gateway authentication test. Add a full signed-token request walkthrough. |
| 5. Persistence and concurrency | How do Flyway, JDBC, constraints and idempotency protect an order? | Planned article; existing PostgreSQL `OrderPersistenceIT`. Extend constraint, rollback and migration-failure experiments. |
| 6. Service boundaries and resilience | How do Gateway, RestClient and Resilience4j handle remote failures? | Planned article; existing real-HTTP error-mapping tests and Compose smoke tests. Add deterministic open/half-open/recovery tests. |
| 7. Operational evidence | How do Actuator health, Micrometer metrics and correlation help explain failure? | Planned article; readiness checks exist. Add metric assertions and a verified trace path before claiming tracing coverage. |
| 8. Packaging and deployment | What changes between a Java process, a Docker container and a Kubernetes workload? | Planned article; Docker/Compose CI exists, Kustomize rendering exists. A live-cluster rollout and recovery exercise is still needed. |

## Keep the test levels explicit

- **Unit tests:** `OrderServiceTest` isolates business decisions with Mockito. Its mocked transaction manager does not prove a database rollback.
- **Container experiments:** `BeanContainerTest` starts real, small Spring contexts. No Boot server is needed.
- **Auto-configuration integration:** `JdbcAutoConfigurationTest` combines Boot, HikariCP, Spring JDBC and H2. It executes SQL.
- **Transaction integration:** `TransactionProxyTest` combines real proxies, a JDBC transaction manager and H2. It inspects rows after the call ends.
- **MVC slices:** controller tests use Spring MVC and security filters, while service or JDBC collaborators are mocked. The `jwt()` test helper supplies an authenticated identity; signature verification is exercised separately by `TokenPolicyTest`.
- **Database integration:** `OrderPersistenceIT` runs Flyway and JDBC against PostgreSQL, including eight concurrent requests.
- **System checks:** Compose smoke tests exercise the running service chain. Kubernetes rendering checks structure; it does not establish cluster behavior.

## Turn a result into an article

Each chapter should contain a concrete question, a small architecture diagram, source links, a reproducible command, named assertions, an observed failure or counterexample, the connection to the commerce application, and the limits of the evidence. Explain Spring Framework, Boot and Cloud separately: object management and transaction infrastructure; application assembly and defaults; distributed-service integrations.

CI executes a clean reactor build, generates the named laboratory results, adds them to the job summary, and uploads `framework-labs-evidence` for 30 days. Link the exact run and source commit when publishing. The report records HEAD and whether the working tree is modified; local results from modified files must not be attributed solely to the recorded commit.

Test results are the primary evidence. Screenshots can illustrate the exact CI summary, an IDE test tree, or a runtime observation; keep the test names, run link and caption with them. A green screenshot by itself does not explain the claim. Do not turn a rendered manifest into a claim of a successful rollout, or a passing functional test into a capacity claim.

The reporter exports named outcomes and source metadata, excluding raw JVM properties, console logs, failure messages and environment variables. Keep the public screenshots similarly focused. Preserve a verified, sanitized result with a published article if the CI artifact may expire; keep build output out of source control.

## Local verification caveat

On some Windows/JDK combinations, Surefire's fork launcher fails when the system temporary directory contains non-ASCII characters. If Maven reports `Unable to access jarfile` before any tests run, select an ASCII-only temporary directory through `MAVEN_OPTS` (`-Djava.io.tmpdir=...`) for that shell. This is a launcher failure, not a passed or failed Spring experiment. No machine-specific path belongs in the project POM.
