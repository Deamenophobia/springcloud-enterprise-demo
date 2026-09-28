# Spring Framework laboratory

Run the 16 experiments with JDK 21 and Maven 3.9+:

```bash
mvn -B -ntp -pl framework-labs clean test
python3 scripts/lab-report.py
```

Use `python` on Windows. No Docker or external database is required.

| Test class | Scenarios |
|---|---|
| `BeanContainerTest` | 7: injection, ambiguity, scopes, lifecycle and configuration methods |
| `JdbcAutoConfigurationTest` | 3: Boot JDBC defaults, custom data source and classpath conditions |
| `TransactionProxyTest` | 6: rollback, self-invocation, checked exceptions and transaction templates |

Reports are generated under `framework-labs/target/evidence/`. Run a clean test first: the reporter reads existing XML and cannot establish freshness. These tests cover small Spring contexts and H2; PostgreSQL, Docker and Kubernetes require their own checks. All laboratory dependencies have test scope.

The complementary articles, diagrams and result figures live in the [blog's GitLab project](https://gitlab.com/Deamenophobia/happykokoro/-/tree/docs/spring-framework-companion/content/spring-framework). Keep long-form editorial content there.
