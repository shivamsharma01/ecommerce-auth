# Auth service

Issues and validates JWTs, handles signup/login, email verification, OAuth2/OIDC (authorization server), and publishes user events to Pub/Sub.

## Requirements

- Java 17
- PostgreSQL and Redis when running for real (not required for `./gradlew test`)

**Database schema** is applied by **Flyway Jobs** in `ecomm-infra/deploy/helm/mcart-bootstrap` (SQL under `files/auth/`). The app does not run migrations on startup. For local PostgreSQL, run Flyway against that folder (see **`ecomm-infra/README.md`** §2 — local migrations).

## Run locally

Default `bootRun` uses the `local` profile:

```bash
./gradlew bootRun
```

Set a JDBC URL, Redis, issuer, mail, and secrets via env or `application-local.yaml`. Minimum examples:

| Variable | Purpose |
|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis |
| `AUTH_ISSUER_URI` | Issuer URL in JWT `iss` (default in yaml: `http://localhost:8081`) |
| `AUTH_VERIFICATION_BASE_URL` | Links in verification emails |
| `JWT_SECRET` | Symmetric secret if your config uses it (see `application.yaml`) |
| `OAUTH2_CLIENT_SECRET`, `OAUTH2_REDIRECT_URI` | Registered OAuth2 client |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP (e.g. Gmail app password) |
| `GCP_PROJECT_ID` | Pub/Sub; use Workload Identity on GKE instead of keys when possible |

Override profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Build and test

```bash
./gradlew build
```

Tests use the `test` profile (in-memory H2, Redis auto-config disabled, stub `StringRedisTemplate`).

## Platform admin (bootstrap)

Flyway in **`ecomm-infra/deploy/helm/mcart-bootstrap/files/auth/`** creates a bootstrap identity:

| | |
|--|--|
| **Email / login identifier** | `bootstrap.admin@mcart.internal` |
| **Initial password** | `ChangeMeAfterFirstDeploy!` |
| **JWT** | Access tokens include OAuth2 scope **`product.admin`** (`SCOPE_product.admin` in resource servers). |

Set `auth_user.platform_admin = true` in the database for any other account that should receive that scope. To replace the bootstrap password hash in Flyway SQL, run:

`./gradlew generateBootstrapPasswordHash -PbootstrapPassword='YourPassword'`

## Kubernetes

Manifests live in **`ecomm-infra/deploy/k8s/apps/auth/`**. Apply via `cd ecomm-infra/deploy && make apps-apply` (or point Argo CD at that path). Production secrets: copy **`secret.example.yaml`** → **`secret.yaml`** (gitignored) — see **`ecomm-infra/README.md`** §3.
