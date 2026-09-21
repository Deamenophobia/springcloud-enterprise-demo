# Design and boundaries

## Business flow

The caller presents an access token. Gateway authorization checks the route's scope, and each business service validates the signature and claims again. The JWT subject is the customer identity; the API never accepts a customer identity from the request body.

For a new order, the order service fetches a trusted price from the catalog using the caller's token. This reference uses one API audience across its services, so the token needs both orders.write and catalog.read. An organization with separate audiences should implement token exchange or a service identity rather than forwarding a token that the receiving service cannot validate.

The database stores a price snapshot and calculates the total using BigDecimal. This demo's only currency is USD. It creates an order record, without inventory reservation, payment processing or fulfillment.

## Idempotency and transactions

The unique constraint is on (customer_id, idempotency_key). Two customers can use the same key independently. A retry is served before calling the catalog, so an upstream outage does not prevent replay.

On first submission, the service calls the catalog outside the transaction. Within a short PostgreSQL READ COMMITTED transaction, INSERT ... ON CONFLICT DO NOTHING chooses a winner. A losing request reads the committed winning record in the next statement and compares the original SKU and quantity. Changed payloads return 409; unchanged payloads receive the same order and price snapshot.

There is no automatic POST retry at the gateway. Callers can safely retry with the same key. Keys remain for the lifetime of their orders; production retention policy must preserve this API guarantee.

The two services own separate databases and migrations. No distributed transaction is claimed. For future events such as OrderCreated, add a transactional outbox plus idempotent consumers; inserting a row and publishing a message independently would introduce a failure gap.

## Failure handling

The catalog HTTP client has a 1-second connection timeout and 2-second read timeout. Spring Cloud CircuitBreaker wraps the call with Resilience4j. The additional time limiter and circuit-breaker executor are disabled because the synchronous transport already enforces the HTTP deadline; this avoids leaving work on a separate executor after a timeout. There is no fabricated successful fallback.

Catalog 404 becomes 422 on order creation, token/scope rejection becomes 403, and unavailable service/circuit-open failures become 503. Validation failures use Spring's ProblemDetail support. Readiness includes database connectivity, while liveness deliberately excludes database and downstream health so a shared outage does not cause restart storms.

## Security choices

Demo mode must be explicitly selected and requires a random secret containing at least 32 bytes. Production mode uses HTTPS OIDC issuer and JWKS endpoints and public-key verification. Both modes validate the issuer, audience, timestamps, and a nonempty subject of at most 255 characters.

Containers run as UID 10001 with a read-only filesystem, dropped capabilities and a writable bounded /tmp. Pods do not mount a Kubernetes API credential. The gateway has an explicit route allowlist, a 64 KB request-size filter and bounded upstream waits. HTTP Basic, session login and cookies are not used by the API.

NetworkPolicies isolate inbound service access. Their enforcement depends on the cluster's CNI. Egress is intentionally unrestricted in this reference; production must adapt egress rules to its database, DNS and identity-provider addresses. JWTs travel over internal HTTP here. Use the platform's workload mTLS if that is required by your environment.

## Operations and limits

Actuator publishes minimal health output and protected Prometheus metrics on a separate port. Logs use Spring Boot's structured ECS format. Distributed tracing, dashboards, alert rules, rate limiting and SLOs are follow-on platform integrations, not implemented features of this sample.

Kubernetes production resources include two replicas, CPU autoscaling to six, a PodDisruptionBudget and a preferred node-spreading constraint. HPA requires metrics-server. These settings are starting values to load-test; HPA does not increase database capacity. At six replicas, each service's 10-connection pool may consume 60 connections plus rollout headroom.

Flyway runs at service startup and serializes migration execution. Its runtime credential therefore needs DDL access in the example. A production migration Job with a separate DDL identity and a least-privileged application role is a recommended next adaptation. Use expand/contract migrations across rolling releases; image rollback cannot undo a database migration safely.

The bundled catalog seed is a learning fixture. The PostgreSQL StatefulSets are single-instance demo databases with persistent volumes, without HA or automated backups. The production overlay deliberately supplies no PostgreSQL server and expects externally operated databases.
