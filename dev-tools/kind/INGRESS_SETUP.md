# Ingress Configuration for KinD Cluster

## Overview

This update adds NGINX Ingress Controller support to the KinD cluster, enabling unified access to all structures-server services through port 80 (HTTP).

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

After deploying with the ingress controller, you can access all services through `http://localhost`:

- **Web UI**: http://localhost/ or http://127.0.0.1/
- **OpenAPI**: http://localhost/api/ or http://127.0.0.1/api/
- **GraphQL**: http://localhost/graphql/ or http://127.0.0.1/graphql/

## Direct Port Access (Still Available)

The NodePort mappings remain available for direct access:
- Web UI: http://localhost:9090
- OpenAPI: http://localhost:8080
- GraphQL: http://localhost:4000

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
# Check ingress resources
kubectl get ingress --context kind-structures-cluster

# Check ingress controller status
kubectl get pods -n ingress-nginx --context kind-structures-cluster

# Test the endpoints
curl http://localhost/health
curl http://localhost/api/
curl http://localhost/graphql/
```

## Troubleshooting

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
