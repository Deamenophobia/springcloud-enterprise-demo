# Deployment and operating guide

## Prerequisites

For Compose: Docker with Linux containers, Compose v2, Python 3 and free host port 8080.

For Kubernetes: a cluster compatible with networking.k8s.io/v1, autoscaling/v2 and policy/v1, a matching kubectl client with Kustomize support, Bash, Python 3, and images reachable from cluster nodes. Demo storage needs a default StorageClass. Production needs metrics-server for HPA and at least two worker nodes for useful spreading. A CNI that enforces NetworkPolicy is needed for network isolation.

The scripts require an explicit context but use its existing credentials. They never install a cluster, change your default context, provision cloud accounts, or delete persistent data.

## Configuration

| Variable | Purpose |
|---|---|
| JWT_MODE | oidc by default; demo must be explicitly selected |
| JWT_ISSUER | Exact expected token issuer |
| JWT_AUDIENCE | Expected audience; commerce-api by default |
| JWT_JWK_SET_URI | HTTPS public signing-key endpoint in oidc mode |
| JWT_SECRET | Base64-encoded random 32+ byte key in demo mode only |
| DB_URL / DB_USER / DB_PASSWORD | Per-service JDBC connection |
| CATALOG_URL | Order client and gateway catalog address |
| ORDER_URL | Gateway order-service address |
| SERVER_PORT / MANAGEMENT_SERVER_PORT | Optional host debugging port overrides |

For deployment input, database variables are prefixed CATALOG_ and ORDER_; the deployment helper turns them into separate service Secrets. Runtime ConfigMap and Secrets are applied before workloads. A rollout restart updates environment values in existing pods.

Environment files are local trusted shell files loaded by deploy.sh. Protect them and never commit them. Kubernetes Secret data is not encryption; configure encryption at rest, RBAC and your organization's secret manager. For a managed installation, replace the local-file helper with your approved secret provisioning system.

## Local images with kind

First create or select a kind cluster using the instructions for your environment. With a running cluster named commerce:

```bash
bash scripts/build-images.sh commerce dev
kind load docker-image commerce/gateway:dev commerce/catalog-service:dev commerce/order-service:dev --name commerce
python3 scripts/init-env.py
bash scripts/deploy.sh demo kind-commerce commerce dev .env
kubectl --context kind-commerce -n commerce-demo port-forward service/gateway 8080:8080
```

In another terminal run `python3 scripts/smoke-test.py`. Demo namespace: commerce-demo. Production namespace: commerce. Do not use the demo overlay for real customer data.

## Registry images

```bash
docker login YOUR_REGISTRY
bash scripts/build-images.sh YOUR_REGISTRY/team/commerce RELEASE_TAG --push
bash scripts/deploy.sh demo YOUR_CONTEXT YOUR_REGISTRY/team/commerce RELEASE_TAG .env
```

The scripts require a tag, but cannot establish that your registry enforces immutability. Use immutable release tags, retain image digests and pin approved base images by digest for reproducible production releases.

For private images, create an imagePullSecret using your registry's prescribed credential flow and add imagePullSecrets to the Deployment pod spec (and to any database pods using a private mirror). The example leaves cloud-specific registry authentication to your platform.

## Production deployment

1. Provision two PostgreSQL databases, their roles, backups and connection budgets. Enable TLS hostname verification. Set each application's connection URL to include sslmode=verify-full. When your database uses a private CA, add sslrootcert=/etc/postgresql/ca.crt and mount the certificate there through a ConfigMap or Secret in both business-service Deployments. The JVM/driver must have a valid trust configuration before deploying.
2. Configure your identity provider to issue signed JWT access tokens with the exact issuer, commerce-api audience, subject, issued-at, expiry, and space-separated scope claim (or the supported scp claim). Grant scopes according to the API table. Supply its HTTPS JWKS URL. The sample Nimbus decoder expects RS256 by default; adapt it explicitly if your provider uses another asymmetric algorithm.
3. Build and scan release images, authenticate the cluster to the registry, and set your CPU/memory and database pool budgets from load tests.
4. Copy .env.production.example to .env.production, fill in the actual values, and restrict local access to the file.
5. Deploy:

```bash
bash scripts/deploy.sh production YOUR_CONTEXT YOUR_REGISTRY/team/commerce RELEASE_TAG .env.production
kubectl --context YOUR_CONTEXT -n commerce get deployments,pods,services,hpa,pdb
```

6. Install/configure the platform's ingress controller and TLS certificate through its normal process. Customize k8s/examples/ingress.yaml: ingressClassName, host, and TLS Secret. The NetworkPolicy assumes the controller's namespace is ingress-system; change that selector to the real controller namespace. Then apply the ingress separately:

```bash
kubectl --context YOUR_CONTEXT apply -f k8s/examples/ingress.yaml
```

7. Use a real identity-provider token for API acceptance testing. Do not use the demo-token utility in production. Confirm readiness, an authenticated product read, order creation/replay, and ownership isolation.

The deployment script does not run a smoke test against production because it would create test business data. The supplied smoke test uses demo signing and belongs in demo/CI environments. Production release acceptance should use an approved test tenant.

## Rollouts and rollback

The script renders a manifest to .rendered/demo.yaml or .rendered/production.yaml; that file contains workload specs and image references, not credentials. It validates environment values first, applies runtime configuration, applies the workload manifest and waits for each Deployment.

A timeout returns nonzero and leaves the failed rollout visible for diagnosis; it does not silently claim success or automatically revert schema changes.

```bash
kubectl --context YOUR_CONTEXT -n commerce rollout history deployment/order-service
bash scripts/rollback.sh YOUR_CONTEXT commerce order-service 2
```

Choose the actual revision shown by rollout history. Kubernetes rollout undo restores a Deployment pod template only. It does not revert a ConfigMap, Secret, database migration or data. Preserve the previous release's environment and schema compatibility plan. If rollback needs earlier runtime settings, redeploy the earlier image tag with the matching environment file.

## Health, logs and metrics

```bash
kubectl --context YOUR_CONTEXT -n commerce get pods
kubectl --context YOUR_CONTEXT -n commerce logs deployment/order-service --tail=100
kubectl --context YOUR_CONTEXT -n commerce describe deployment/order-service
kubectl --context YOUR_CONTEXT -n commerce get events --sort-by=.lastTimestamp
kubectl --context YOUR_CONTEXT -n commerce port-forward service/order-service 9090:9090
curl -fsS http://localhost:9090/actuator/health/readiness
```

Port-forwarding is a local debugging tunnel, not a public ingress. Configure Prometheus in the monitoring namespace to scrape each service's management port with a metrics.read token and appropriate token rotation. Never put bearer tokens in committed scrape configurations.

## Troubleshooting

| Symptom | Check |
|---|---|
| Docker cannot connect | Start Docker Desktop/Engine and select Linux containers |
| 401 | Token expiry, signing key, issuer and audience |
| 403 creating an order | orders.write scope plus catalog.read for the forwarded catalog call |
| 409 on POST | Generate a new idempotency key for a different request |
| 503 on POST | Catalog readiness, DNS, network policy, timeout/circuit-breaker state |
| Pods Pending | PVC StorageClass, available CPU/memory, scheduling constraints |
| ImagePullBackOff | Image name/tag, registry permissions and imagePullSecrets |
| Database auth failure after editing .env | Existing database volumes retain their original credentials |
| Readiness stays DOWN | Database endpoint, credentials, TLS trust, Flyway migration logs |
| HPA shows unknown | metrics-server availability and CPU requests |
| Gateway inaccessible through ingress | Controller class, host, TLS, and controller namespace NetworkPolicy |

## CI and release boundaries

The GitHub Actions workflow verifies Java and PostgreSQL behavior, shell syntax/lint, Kustomize rendering, Docker builds and end-to-end API behavior. It does not publish images or deploy automatically. Registry pushes and cluster deployment are explicit script invocations.

Before adopting the reference for production, add the organization's vulnerability scanning, SBOM/signing, secret manager integration, telemetry, restore drills, approved CI action digests and environment-specific release controls.
