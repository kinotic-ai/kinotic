# KinD Image Loading Strategy

**Issue**: KinD clusters cannot pull images directly from Docker registries (docker.io, gcr.io, etc.) because they're isolated from the internet.

**Additional Issue**: Multi-platform images (like Elasticsearch) can fail with `kind load docker-image` due to manifest format issues.

**Solution**: 
1. Pre-pull images on the host machine with explicit platform
2. Use `docker save` to create a tarball
3. Load tarball into KinD cluster using `kind load image-archive`

## Image Loading Methods

### Method 1: Direct Load (Simple Images)
For simple single-platform images:
```bash
docker pull docker.io/bitnami/postgresql:15.5.0
kind load docker-image docker.io/bitnami/postgresql:15.5.0 --name structures-cluster
```

**Use for**: PostgreSQL, Keycloak, structures-server

### Method 2: Tarball Load (Multi-Platform Images)
For complex multi-platform images:
```bash
# Pull for specific platform
docker pull --platform linux/amd64 docker.elastic.co/elasticsearch/elasticsearch:8.18.1

# Save to tarball
docker save docker.elastic.co/elasticsearch/elasticsearch:8.18.1 -o /tmp/elasticsearch.tar

# Load tarball into cluster
kind load image-archive /tmp/elasticsearch.tar --name structures-cluster

# Clean up
rm /tmp/elasticsearch.tar
```

**Use for**: Elasticsearch (official Elastic images)

## Why Two Methods?

### Multi-Platform Image Problem

Official Elasticsearch images are **multi-platform** (support amd64, arm64, etc.). When you run:
```bash
kind load docker-image docker.elastic.co/elasticsearch/elasticsearch:8.18.1
```

KinD tries to load the image but encounters:
```
ERROR: failed to load image: ctr: content digest sha256:xxx: not found
```

This happens because:
1. Docker stores multi-platform images with **manifests** pointing to platform-specific layers
2. `kind load docker-image` doesn't handle these manifests correctly
3. The containerd runtime in KinD nodes can't find the referenced content

### Tarball Method Fixes This

Using `docker save` + `kind load image-archive`:
1. ✅ `docker save` flattens the image into a single-platform tarball
2. ✅ `kind load image-archive` imports the complete image with all layers
3. ✅ No manifest issues - everything is in the tarball
4. ✅ Works reliably for all image types

## Updated Functions

All deployment functions now follow this pattern:

### 1. **Elasticsearch** (v8.11.1)
```bash
# 1. Check if image exists locally
# 2. Pull if needed: docker pull docker.io/bitnami/elasticsearch:8.11.1
# 3. Load into cluster: kind load docker-image <image> --name structures-cluster
# 4. Deploy with Helm, specifying --set image.tag=8.11.1
```

### 2. **PostgreSQL** (v15.5.0)
```bash
# Same pattern:
# docker.io/bitnami/postgresql:15.5.0
```

### 3. **Keycloak** (v26.0.2)
```bash
# Same pattern:
# docker.io/bitnami/keycloak:26.0.2
```

## How It Works

```bash
# Example: Loading Elasticsearch

# Step 1: Pull image from Docker Hub to local Docker
docker pull docker.io/bitnami/elasticsearch:8.11.1

# Step 2: Load image into KinD cluster nodes
kind load docker-image docker.io/bitnami/elasticsearch:8.11.1 --name structures-cluster

# Step 3: Verify image is available in cluster
docker exec structures-cluster-control-plane crictl images | grep elasticsearch

# Step 4: Deploy with Helm (image.pullPolicy defaults to IfNotPresent)
helm upgrade --install elasticsearch bitnami/elasticsearch --set image.tag=8.11.1
```

## Benefits

✅ **Predictable versions** - Pin to known working versions  
✅ **No network issues** - Images loaded from local Docker  
✅ **Faster deployments** - No waiting for image pulls  
✅ **Offline capable** - Works without internet once images are cached  
✅ **Consistent behavior** - Same images used across deployments  

## Version Selection Rationale

| Component | Version | Reason |
|-----------|---------|--------|
| Elasticsearch | 8.11.1 | Stable LTS version, compatible with Structures |
| PostgreSQL | 15.5.0 | Latest stable PG 15.x, matches production usage |
| Keycloak | 26.0.2 | Matches docker-compose setup exactly |

## Troubleshooting

### Image Pull Fails
```bash
# Check Docker Hub rate limits
docker pull docker.io/bitnami/elasticsearch:8.11.1

# If rate limited, authenticate:
docker login
```

### Image Load Fails
```bash
# Check KinD cluster exists
kind get clusters

# Check cluster is running
kubectl cluster-info --context kind-structures-cluster

# Manual load test
kind load docker-image docker.io/bitnami/elasticsearch:8.11.1 --name structures-cluster
```

### Pod Still Shows ImagePullBackOff
```bash
# Check image is in cluster
docker exec structures-cluster-control-plane crictl images

# Check pod image specification
kubectl get pod <pod-name> -o yaml | grep image:

# Make sure image.pullPolicy is not Always
kubectl get deployment <name> -o yaml | grep pullPolicy
```

## Manual Image Loading

If you need to pre-load images before deployment:

```bash
# Pre-load all dependency images
docker pull docker.io/bitnami/elasticsearch:8.11.1
docker pull docker.io/bitnami/postgresql:15.5.0
docker pull docker.io/bitnami/keycloak:26.0.2

kind load docker-image docker.io/bitnami/elasticsearch:8.11.1 --name structures-cluster
kind load docker-image docker.io/bitnami/postgresql:15.5.0 --name structures-cluster
kind load docker-image docker.io/bitnami/keycloak:26.0.2 --name structures-cluster

# Verify
docker exec structures-cluster-control-plane crictl images | grep bitnami
```

## Automated in Deploy Script

The `deploy.sh` functions now automatically:
1. Check if image exists locally (`docker image inspect`)
2. Pull if missing (`docker pull`)
3. Load into KinD (`kind load docker-image`)
4. Deploy with Helm (with pinned version)

No manual intervention needed! Just run:
```bash
./dev-tools/kind/kind-cluster.sh deploy
```

## Future Improvements

Consider adding:
- Image pre-caching script (`load-images.sh`)
- Version configuration in `config/versions.yaml`
- Multi-architecture support (arm64/amd64)
- Local registry for faster loading (optional)

