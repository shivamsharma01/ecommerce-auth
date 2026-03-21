# MCart Auth Service

Authentication and authorization microservice for the MCart e-commerce platform. Handles user registration, login, email verification, JWT token issuance, and OAuth2/OIDC flows.

## Capabilities

- **Password Signup** — Email/password registration with mandatory email verification
- **Password Login** — Credential-based authentication with account lockout after failed attempts
- **Social Login** — OAuth2 login via Google, Facebook, Apple with auto-provisioning
- **Email Verification** — Token-based verification links with rate limiting and resend support
- **JWT Tokens** — RS256-signed access tokens and refresh tokens stored in Redis
- **OAuth2 Authorization Server** — OIDC discovery, JWKS endpoint, authorization code and refresh token grants
- **Outbox Pattern** — Event publishing via Pub/Sub for downstream services (User Service)
- **Cloud SQL Proxy** — GKE deployment with Cloud SQL Proxy sidecar

## Tech Stack

- Java 17, Spring Boot 4.x
- PostgreSQL (Flyway migrations)
- Redis (refresh tokens, login attempt tracking, rate limiting)
- GCP Pub/Sub (outbox events)
- Spring Security OAuth2 Authorization Server

## Configuration (Kubernetes)

All configuration is externalized via environment variables. In Kubernetes, inject these from **ConfigMaps** (non-sensitive) and **Secrets** (credentials).

### Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_SECRET` | Secret for JWT signing (use strong random value) | — |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/auth` |
| `DB_USERNAME` | Database username | `auth_user` |
| `DB_PASSWORD` | Database password | — |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | — |
| `AUTH_ISSUER_URI` | OAuth2 issuer URI (auth service base URL) | `https://auth.example.com` |
| `AUTH_VERIFICATION_BASE_URL` | Base URL for verification email links | `https://auth.example.com` |
| `SPRING_MAIL_USERNAME` | SMTP username (Gmail: your email) | — |
| `SPRING_MAIL_PASSWORD` | SMTP password (Gmail: App Password) | — |
| `GCP_PROJECT_ID` | GCP project for Pub/Sub | — |
| `GCP_CREDENTIALS_PATH` | Path to GCP service account JSON (optional with Workload Identity) | — |

### OAuth2 Client (for Authorization Code flow)

| Variable | Description | Example |
|----------|-------------|---------|
| `OAUTH2_CLIENT_ID` | Registered client ID | `client-app` |
| `OAUTH2_CLIENT_SECRET` | Client secret (plain text, encoded internally) | — |
| `OAUTH2_REDIRECT_URI` | Redirect URI after authorization | `https://app.example.com/login/oauth2/code/client-app` |

### Optional (with defaults)

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port | `8081` |
| `JWT_ACCESS_TTL_SECONDS` | Access token TTL | `900` (15 min) |
| `JWT_REFRESH_TTL_SECONDS` | Refresh token TTL | `2592000` (30 days) |
| `LOGIN_MAX_ATTEMPTS` | Failed attempts before lockout | `5` |
| `LOGIN_LOCK_DURATION_MINUTES` | Lockout duration | `15` |
| `EMAIL_VERIFICATION_RATE_LIMIT` | Max verification emails per hour per user | `3` |
| `EMAIL_VERIFICATION_TTL_HOURS` | Verification link expiry (hours) | `24` |
| `AUTH_PUBSUB_USER_SIGNUP_TOPIC` | Pub/Sub topic for user signup events | `user-signup-events` |
| `AUTH_COOKIE_SECURE` | Set `false` for local HTTP (cookie over non-HTTPS) | `true` |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/signup` | Register with email/password |
| POST | `/auth/login` | Login with email/password |
| POST | `/auth/social/login` | Login with social provider token |
| POST | `/auth/refresh` | Refresh access token (cookie: `refresh_token`) |
| GET | `/auth/verify-email?token=...` | Verify email via link |
| POST | `/auth/resend-verification` | Resend verification email |
| GET | `/.well-known/openid-configuration` | OIDC discovery |
| GET | `/oauth2/jwks` | JWKS for token validation |

## Gmail SMTP (Email Verification)

Google requires an **App Password** for SMTP (not your regular Gmail password).

1. Enable [2-Step Verification](https://myaccount.google.com/security)
2. Create an [App Password](https://myaccount.google.com/apppasswords) (Mail, custom device)
3. Set `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` (16-char App Password, no spaces)

**SMTP:** smtp.gmail.com:587, STARTTLS, auth required.

## GKE Deployment

### Pod Structure

```
Pod
├── auth-service (Spring Boot)
└── cloud-sql-proxy (sidecar)
```

### Cloud SQL Proxy (recommended: Workload Identity)

```yaml
- name: cloud-sql-proxy
  image: gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.8.0
  args:
    - "--structured-logs"
    - "--port=5432"
    - "PROJECT:REGION:INSTANCE"
```

Use Workload Identity so the proxy authenticates via IAM (no JSON key files).

### ConfigMap Example

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: auth-config
data:
  DB_URL: "jdbc:postgresql://127.0.0.1:5432/auth"
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  AUTH_ISSUER_URI: "https://auth.example.com"
  AUTH_VERIFICATION_BASE_URL: "https://auth.example.com"
  OAUTH2_REDIRECT_URI: "https://app.example.com/login/oauth2/code/client-app"
  GCP_PROJECT_ID: "my-project"
```

### Secrets Example

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: auth-secrets
type: Opaque
stringData:
  JWT_SECRET: "<strong-random-secret>"
  DB_PASSWORD: "<db-password>"
  REDIS_PASSWORD: "<redis-password>"
  SPRING_MAIL_USERNAME: "noreply@example.com"
  SPRING_MAIL_PASSWORD: "<gmail-app-password>"
  OAUTH2_CLIENT_SECRET: "<client-secret>"
```

## Local Development

```bash
# Set required env vars or use application-local.yaml
export DB_URL=jdbc:postgresql://localhost:5432/auth
export REDIS_HOST=localhost
export AUTH_ISSUER_URI=http://localhost:8081
export AUTH_VERIFICATION_BASE_URL=http://localhost:8081

./gradlew bootRun
```

Runs with `--spring.profiles.active=local` by default.

## Build

```bash
./gradlew build
```

## License

Proprietary — MCart
