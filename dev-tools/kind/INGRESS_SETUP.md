# Ingress Configuration for KinD Cluster

## Overview

The KinD cluster uses NGINX Ingress Controller for unified access to all structures-server services with **HTTPS/TLS** support.

### Key Features

- **HTTPS by default** - All services accessible via `https://localhost`
- **WebSocket support** - Dedicated ingress for STOMP with sticky sessions
- **Path-based routing** - `/api/`, `/graphql/`, `/v1`, `/`
- **Automatic TLS** - Uses mkcert (if available) or cert-manager self-signed certificates

## Ingress Architecture

The structures-server uses **two separate Ingress resources** to handle different traffic types:

### 1. WebSocket Ingress (`structures-server-ws`)

Handles STOMP WebSocket connections at `/v1`:
- **Sticky sessions** via cookie affinity
- **Long timeouts** for persistent connections (1 hour)
- **HTTP/1.1** protocol for WebSocket upgrade
- Path: `/v1` → port 58503

### 2. HTTP Ingress (`structures-server-http`)

Handles standard HTTP traffic with path rewrites:
- `/api/*` → port 8080 (OpenAPI)
- `/graphql/*` → port 4000 (GraphQL)
- `/*` → port 9090 (Static UI)

This split architecture:
- ✅ Eliminates the need for `configuration-snippet` annotations
- ✅ Prevents nginx directive conflicts
- ✅ Allows different timeout/affinity settings per traffic type
- ✅ Works with default nginx-ingress security settings

## Changes Made

### 1. KinD Cluster Configuration
**File:** `dev-tools/kind/config/kind-config.yaml`

Added port mappings for the ingress controller:
- Port 80 (HTTP) → containerPort 80
- Port 443 (HTTPS) → containerPort 443

### 2. Ingress Template
**File:** `helm/structures/templates/ingress.yaml`

Created a new ingress resource that routes traffic based on URL paths:
- `/api/*` → port 8080 (OpenAPI)
- `/graphql/*` → port 4000 (GraphQL)
- `/*` → port 9090 (Web UI)

The ingress uses regex path matching and rewrites to forward requests to the appropriate backend services.

### 3. Helm Values
**Files:** 
- `helm/structures/values.yaml` - Added ingress configuration (disabled by default)
- `dev-tools/kind/config/helm-values.yaml` - Enabled ingress for KinD cluster

### 4. Deployment Script
**File:** `dev-tools/kind/lib/deploy.sh`

Added `deploy_nginx_ingress()` function that:
- Checks if ingress controller is already deployed
- Applies the official KinD ingress-nginx manifest
- Waits for pods to be ready

### 5. Cluster Script
**File:** `dev-tools/kind/kind-cluster.sh`

Integrated ingress controller deployment into the cluster creation workflow.

## Access URLs

After deploying with the ingress controller, services are available via **HTTPS**:

### Via Ingress (HTTPS) - Recommended

| Path | Service | Protocol |
|------|---------|----------|
| `https://localhost/` | Static UI (SPA) | HTTPS |
| `https://localhost/api/` | OpenAPI REST | HTTPS |
| `https://localhost/graphql/` | GraphQL | HTTPS |
| `wss://localhost/v1` | STOMP WebSocket | WSS (sticky sessions) |

### Via NodePort (Direct, no TLS)

The NodePort mappings remain available for direct access without TLS:

| Service | URL |
|---------|-----|
| Web UI | http://localhost:9090 |
| OpenAPI | http://localhost:8080 |
| GraphQL | http://localhost:4000 |
| STOMP | ws://localhost:58503 |

## TLS Certificate Setup

The deployment automatically configures TLS certificates using one of two methods:

### Option 1: mkcert (Recommended for Local Development)

If [mkcert](https://github.com/FiloSottile/mkcert) is installed, the deploy script generates locally-trusted certificates that browsers accept without warnings.

**Installation:**

```bash
# macOS
brew install mkcert
brew install nss  # Required for Firefox support
mkcert -install   # Install local CA (one-time, may require sudo)

# Linux (Ubuntu/Debian)
sudo apt install libnss3-tools  # Required for Firefox/Chrome
brew install mkcert             # Or use pre-built binary (see below)
mkcert -install

# Linux (pre-built binary)
curl -JLO "https://dl.filippo.io/mkcert/latest?for=linux/amd64"
chmod +x mkcert-v*-linux-amd64
sudo mv mkcert-v*-linux-amd64 /usr/local/bin/mkcert
mkcert -install
```

**Result:** `https://localhost` works with full browser trust (green padlock).

### Option 2: cert-manager Self-Signed (Fallback)

If mkcert is not installed, cert-manager automatically generates self-signed certificates.

**Result:** `https://localhost` works but browsers show "Not Secure" warning. Click "Advanced" → "Proceed to localhost" to continue.

### How It Works

```
Deploy Script Flow:
1. Check if mkcert is installed
2. If yes: Generate certs → Create K8s TLS secret → Set existingSecret=true
3. Always: Install cert-manager
4. Deploy Helm chart
5. If no mkcert secret: cert-manager creates self-signed Certificate
```

The Helm chart's `certificate.yaml` template only creates cert-manager resources when `ingress.tls.existingSecret=false`.

## Deployment Instructions

### For New Clusters

Simply create the cluster as usual:

```bash
./dev-tools/kind/kind-cluster.sh deploy
```

The ingress controller will be automatically deployed.

### For Existing Clusters

You have two options:

#### Option 1: Recreate the Cluster (Recommended)

This ensures the port mappings are correct:

```bash
# Delete the existing cluster
./dev-tools/kind/kind-cluster.sh delete

# Create a new cluster with ingress support
./dev-tools/kind/kind-cluster.sh deploy
```

#### Option 2: Manual Ingress Setup (If cluster recreation is not possible)

**Note:** This won't add port 80/443 mappings, so you'll need to use port forwarding.

1. Deploy the ingress controller manually:
   ```bash
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml --context kind-structures-cluster
   ```

2. Wait for it to be ready:
   ```bash
   kubectl wait --namespace ingress-nginx \
     --for=condition=ready pod \
     --selector=app.kubernetes.io/component=controller \
     --timeout=90s \
     --context kind-structures-cluster
   ```

3. Deploy/update structures-server with ingress enabled:
   ```bash
   helm upgrade --install structures-server ./helm/structures \
     --kube-context kind-structures-cluster \
     --values dev-tools/kind/config/helm-values.yaml
   ```

4. Set up port forwarding to access via localhost:
   ```bash
   kubectl port-forward -n ingress-nginx service/ingress-nginx-controller 80:80 --context kind-structures-cluster
   ```

## Verification

After deployment, verify the ingress is working:

```bash
# Check ingress resources (should show two: structures-server-ws and structures-server-http)
kubectl get ingress --context kind-structures-cluster

# Check ingress controller status
kubectl get pods -n ingress-nginx --context kind-structures-cluster

# Check TLS secret exists
kubectl get secret structures-tls-secret --context kind-structures-cluster

# Test HTTPS endpoints (-k to allow self-signed if mkcert not used)
curl -k https://localhost/
curl -k https://localhost/api/
curl -k https://localhost/graphql/

# Test HTTP redirect (should return 308 redirect to HTTPS)
curl -I http://localhost/
```

## Troubleshooting

### Admission webhook denies configuration-snippet

**Error:**
```
Error: admission webhook "validate.nginx.ingress.kubernetes.io" denied the request: 
nginx.ingress.kubernetes.io/configuration-snippet annotation cannot be used. 
Snippet directives are disabled by the Ingress administrator
```

**Cause:** The nginx-ingress controller has `allow-snippet-annotations` disabled by default (security feature).

**Solution:**
```bash
kubectl patch configmap ingress-nginx-controller \
  -n ingress-nginx \
  --context kind-structures-cluster \
  --type merge \
  -p '{"data":{"allow-snippet-annotations":"true"}}'

kubectl rollout restart deployment ingress-nginx-controller -n ingress-nginx --context kind-structures-cluster
```

### Admission webhook denies "risky annotation"

**Error:**
```
Error: admission webhook "validate.nginx.ingress.kubernetes.io" denied the request: 
annotation group ConfigurationSnippet contains risky annotation based on ingress configuration
```

**Cause:** The nginx-ingress controller validates snippet content and blocks directives like `proxy_set_header`, `rewrite`, `if` which are considered "risky".

**Solution:**
```bash
kubectl patch configmap ingress-nginx-controller \
  -n ingress-nginx \
  --context kind-structures-cluster \
  --type merge \
  -p '{"data":{"allow-snippet-annotations":"true","annotations-risk-level":"Critical"}}'

kubectl rollout restart deployment ingress-nginx-controller -n ingress-nginx --context kind-structures-cluster
```

> **⚠️ Warning:** These settings are for development only. See [Security Notice](#️-security-notice---development-only) above.

### Ingress has no ADDRESS

This is normal for KinD clusters. The ingress works through the port mappings defined in `kind-config.yaml`.

### Cannot reach services via localhost

1. Verify the ingress controller is running:
   ```bash
   kubectl get pods -n ingress-nginx --context kind-structures-cluster
   ```

2. Check the ingress resource:
   ```bash
   kubectl describe ingress structures-server --context kind-structures-cluster
   ```

3. Verify structures-server pods are running:
   ```bash
   kubectl get pods -l app=structures-server --context kind-structures-cluster
   ```

4. Check if port 80 is already in use:
   ```bash
   lsof -i :80
   ```

### Path routing not working

Check the ingress annotations and path configuration:
```bash
kubectl get ingress structures-server -o yaml --context kind-structures-cluster
```

The rewrite-target annotation should be: `/$2`
This captures the path after the prefix and forwards it to the backend.
