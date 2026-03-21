# Kubernetes Deployment

## Prerequisites

- Spring Boot Actuator with health probes (`/actuator/health/liveness`, `/actuator/health/readiness`)
- Container image built and pushed to Artifact Registry

## Deploy

1. **Replace placeholders** in `deployment.yaml`:
   - `<ARTIFACT_REGISTRY_URL>`: e.g. `gcr.io/my-project` or `us-docker.pkg.dev/my-project/docker`
   - `<VERSION>`: commit SHA (`git rev-parse --short HEAD`) or semantic version

2. **Create secrets** (if not using External Secrets):
   ```bash
   kubectl create secret generic auth-secrets \
     --from-literal=JWT_SECRET=<secret> \
     --from-literal=DB_PASSWORD=<password> \
     --from-literal=REDIS_PASSWORD=<password> \
     --from-literal=SPRING_MAIL_USERNAME=<email> \
     --from-literal=SPRING_MAIL_PASSWORD=<app-password> \
     --from-literal=OAUTH2_CLIENT_SECRET=<client-secret>
   ```

3. **Update ConfigMap** with your environment values.

4. **Apply**:
   ```bash
   kubectl apply -f k8s/
   ```

## CI/CD Image Tag

Use commit SHA for traceability:
```bash
export VERSION=$(git rev-parse --short HEAD)
docker build -t $ARTIFACT_REGISTRY_URL/auth:$VERSION .
docker push $ARTIFACT_REGISTRY_URL/auth:$VERSION
# Then sed or envsubst to replace <VERSION> in deployment.yaml
```
