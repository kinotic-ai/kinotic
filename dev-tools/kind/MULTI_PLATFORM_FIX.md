# Multi-Platform Image Loading Fix

## Problem

**All** modern Docker images from Docker Hub are multi-platform (support amd64, arm64, etc.):
- ❌ `postgres:15-alpine` - multi-platform
- ❌ `docker.elastic.co/elasticsearch/elasticsearch:8.18.1` - multi-platform  
- ❌ `docker.io/bitnami/keycloak:26.0.2` - multi-platform

When using `kind load docker-image` on these images, you get:
```
ERROR: failed to load image: ctr: content digest sha256:xxx: not found
```

## Root Cause

Multi-platform images have a **manifest list** that points to platform-specific image layers:
```
postgres:15-alpine (manifest list)
├── linux/amd64 → sha256:aaa...
├── linux/arm64 → sha256:bbb...
└── linux/arm/v7 → sha256:ccc...
```

When you pull without `--platform`, Docker gets the manifest list. When KinD tries to load it, containerd looks for specific layer digests that aren't in the manifest list itself.

## Solution: Re-Tag Method

**For EVERY multi-platform image**, use this pattern:

```bash
# 1. Pull with explicit platform
docker pull --platform linux/amd64 postgres:15-alpine

# 2. Re-tag to localhost namespace (creates clean single-platform ref)
docker tag postgres:15-alpine localhost/postgres:15-alpine

# 3. Load the localhost-tagged image
kind load docker-image localhost/postgres:15-alpine --name structures-cluster

# 4. Re-tag inside cluster to original name
for node in $(kind get nodes --name structures-cluster); do
    docker exec $node ctr -n k8s.io images tag \
        localhost/postgres:15-alpine \
        postgres:15-alpine
done
```

## Why This Works

| Step | What It Does |
|------|--------------|
| Pull with `--platform` | Downloads single-platform image (amd64) |
| Tag to `localhost/*` | Creates new reference **without** manifest list |
| Load localhost tag | KinD loads single-platform image successfully |
| Re-tag in cluster | Helm/Kubernetes can use original image name |

The `localhost/` namespace creates a **fresh image reference** that's clean of multi-platform manifest issues!

## Applied to All Components

### ✅ Elasticsearch
```bash
docker pull --platform linux/amd64 docker.elastic.co/elasticsearch/elasticsearch:8.18.1
docker tag ... localhost/elasticsearch:8.18.1
kind load docker-image localhost/elasticsearch:8.18.1
```

### ✅ PostgreSQL  
```bash
docker pull --platform linux/amd64 postgres:15-alpine
docker tag postgres:15-alpine localhost/postgres:15-alpine
kind load docker-image localhost/postgres:15-alpine
```

### ✅ Keycloak
```bash
docker pull --platform linux/amd64 docker.io/bitnami/keycloak:26.0.2
docker tag ... localhost/keycloak:26.0.2
kind load docker-image localhost/keycloak:26.0.2
```

## Alternative Methods (Why They Don't Work)

### ❌ Method 1: `kind load docker-image <original>`
```bash
kind load docker-image postgres:15-alpine
# ERROR: manifest list issues
```

### ❌ Method 2: `docker save` + `kind load image-archive`
```bash
docker save postgres:15-alpine -o postgres.tar
kind load image-archive postgres.tar
# ERROR: Still includes manifest references
```

### ✅ Method 3: Re-tag (This One!)
```bash
docker tag postgres:15-alpine localhost/postgres:15-alpine
kind load docker-image localhost/postgres:15-alpine
# SUCCESS: Clean single-platform image
```

## Try Again

```bash
./dev-tools/kind/kind-cluster.sh deploy
```

You should see:
```
→ Pre-loading PostgreSQL image into cluster...
→ Pulling postgres:15-alpine for linux/amd64...
→ Re-tagging image to local reference...
→ Loading image into KinD cluster...
→ Tagging image in cluster nodes...
✓ PostgreSQL deployed (1/1 pods ready)
```

All three components now use the same reliable loading method! 🎉

## Future: Batch Pre-Loading Script

Could create a helper script to pre-load all images:

```bash
#!/bin/bash
# dev-tools/kind/preload-images.sh

images=(
    "docker.elastic.co/elasticsearch/elasticsearch:8.18.1"
    "postgres:15-alpine"
    "docker.io/bitnami/keycloak:26.0.2"
)

for img in "${images[@]}"; do
    docker pull --platform linux/amd64 "$img"
    local_tag="localhost/${img##*/}"
    docker tag "$img" "$local_tag"
    kind load docker-image "$local_tag" --name structures-cluster
done
```

