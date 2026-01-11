# Kubernetes RBAC Configuration for Ignite Clustering

## Overview

This document explains the Kubernetes RBAC (Role-Based Access Control) configuration required for Apache Ignite Kubernetes IP Finder to work properly in the structures-server deployment.

## Problem

Apache Ignite requires access to the Kubernetes API to discover other pods in the cluster for forming the Ignite cluster. Without proper permissions, the pods cannot see each other and clustering fails with errors like:

```
Failed to get Kubernetes services: Forbidden: pods is forbidden
```

## Solution

We've implemented a minimal RBAC configuration that grants the structures-server pods the exact permissions they need for Ignite clustering.

### Files Created

1. **`helm/structures/templates/serviceaccount.yaml`**
   - Creates a ServiceAccount for the structures-server pods
   - This provides an identity that can be granted permissions

2. **`helm/structures/templates/role.yaml`**
   - Defines the specific permissions needed: `get`, `list`, and `watch` on:
     - `pods` - To discover other structures-server pods
     - `endpoints` - To get service endpoint information
     - `services` - To find the structures service
   - Scoped to the namespace where structures-server is deployed (principle of least privilege)

3. **`helm/structures/templates/rolebinding.yaml`**
   - Binds the Role to the ServiceAccount
   - Grants the ServiceAccount the permissions defined in the Role

4. **`helm/structures/templates/structures-server-deployment.yaml`** (updated)
   - Added `serviceAccountName` to use the created ServiceAccount
   - Pods now run with the necessary Kubernetes API permissions

## Elasticsearch Configuration

Also fixed in `dev-tools/kind/config/helm-values.yaml`:

```yaml
elastic:
  connections:
    - host: "elasticsearch-master"  # Internal Elasticsearch service
      port: 9200
      scheme: "http"  # No SSL for local development
  connectionTimeout: "5s"
  socketTimeout: "60s"
  username: ""  # No auth needed
  password: ""
```

This points to the Elasticsearch instance deployed in the KinD cluster.

## Security Considerations

- **Minimal permissions**: Only grants access to read (`get`, `list`, `watch`) pods, endpoints, and services
- **Namespace-scoped**: Role is limited to the namespace where structures-server is deployed
- **No cluster-wide access**: Uses `Role` (not `ClusterRole`) for namespace isolation
- **No write permissions**: Cannot create, update, or delete any resources

## References

- [Apache Ignite Kubernetes IP Finder Documentation](https://ignite.apache.org/docs/latest/clustering/kubernetes)
- [Kubernetes RBAC Documentation](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
- Ignite Configuration: `structures-core/src/main/java/org/mindignited/structures/internal/config/CacheEvictionConfiguration.java`

## Testing

After deploying with these changes:

```bash
# Deploy the updated chart
./dev-tools/kind/kind-cluster.sh deploy

# Verify RBAC resources were created
kubectl get serviceaccount,role,rolebinding -l app=structures-server

# Check that pods can see each other
kubectl logs -l app=structures-server --tail=50 | grep -i "cluster\|ignite"

# Verify Elasticsearch connectivity
kubectl logs -l app=structures-server --tail=50 | grep -i "elasticsearch\|elastic"
```

## Troubleshooting

If clustering still fails:

1. **Check ServiceAccount is bound**:
   ```bash
   kubectl get pods -o yaml | grep serviceAccountName
   ```

2. **Test API access from pod**:
   ```bash
   kubectl exec -it <pod-name> -- sh
   # Inside pod:
   curl -k https://kubernetes.default/api/v1/namespaces/default/pods
   ```

3. **Check RBAC permissions**:
   ```bash
   kubectl auth can-i get pods --as=system:serviceaccount:default:structures-server
   kubectl auth can-i list endpoints --as=system:serviceaccount:default:structures-server
   ```

4. **Review Ignite logs**:
   ```bash
   kubectl logs -l app=structures-server --tail=100 | grep -A 5 -B 5 "TcpDiscovery"
   ```

