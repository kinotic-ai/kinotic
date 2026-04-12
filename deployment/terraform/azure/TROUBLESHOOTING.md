# Azure Troubleshooting

Common issues encountered during deployment and operations. Check this before debugging.

## Pre-Deploy Checks

Run these before `terraform apply` to catch issues early:

```bash
# Verify Azure login and subscription
az account show --query "{name:name, id:id}" -o table

# Check vCPU quota in target region
az vm list-usage --location centralus --query "[?contains(name.value,'standardDSv5Family')].{Name:name.localizedValue, Current:currentValue, Limit:limit}" -o table

# Check supported K8s versions
az aks get-versions --location centralus --query "values[?!isPreview].version" -o tsv | sort -V

# Verify DNS propagation (if not first deploy)
dig NS kinotic.ai

# Verify kubelogin is installed
kubelogin --version
```

## Common Errors

### `SubscriptionNotFound` during bootstrap

**Cause:** Azure resource providers not registered on subscription.
**Fix:** `./bootstrap-state.sh` registers them automatically. Or manually:
```bash
az provider register --namespace Microsoft.Storage
az provider register --namespace Microsoft.Compute
az provider register --namespace Microsoft.ContainerService
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.ManagedIdentity
```

### `AvailabilityZoneNotSupported`

**Cause:** Region or VM SKU doesn't support availability zones.
**Fix:** Change to a zone-supported region (`centralus`, `eastus`, `eastus2`) in `terraform.tfvars`.

### `K8sVersionNotSupported`

**Cause:** K8s version is LTS-only or end-of-life.
**Fix:** Check supported versions and update `terraform.tfvars`:
```bash
az aks get-versions --location centralus --query "values[?!isPreview].version" -o tsv | sort -V
```

### `ErrCode_InsufficientVCPUQuota`

**Cause:** Not enough vCPU quota in the region.
**Fix:** Request increase via Azure Portal > Subscriptions > Quotas > Compute. Beta needs 12 vCPUs (3 x 4). Production needs 36+ (3 system + 3 ES at 8 each).

### `cannot re-use a name that is still in use` (Helm)

**Cause:** A previous helm release failed and is stuck in pending/failed state.
**Fix:**
```bash
helm uninstall <release-name> -n <namespace>
terraform apply
```

### `Unauthorized` on kubernetes data sources

**Cause:** `local_account_disabled = true` requires exec-based auth, not cert-based.
**Fix:** Get credentials and configure kubelogin:
```bash
az aks get-credentials --resource-group rg-kinotic-production --name aks-kinotic-production
kubelogin convert-kubeconfig -l azurecli
```

### `Failed to construct REST client: no client config` (kubernetes_manifest)

**Cause:** `kubernetes_manifest` validates against the K8s API at plan time, before the cluster exists.
**Fix:** Use `kubernetes_storage_class_v1` (for StorageClass) or `terraform_data` with `kubectl apply` (for CRDs like ClusterIssuer, Certificate).

### cert-manager `no such host` for Let's Encrypt

**Cause:** Wrong ACME URL. The correct URL has a zero: `acme-v02` not `acme-v2`.
**Fix:** The correct URL is `https://acme-v02.api.letsencrypt.org/directory`. If the ClusterIssuer was created with the wrong URL:
```bash
kubectl delete clusterissuer letsencrypt-prod
kubectl delete secret letsencrypt-prod-account-key -n cert-manager
# Re-apply via terraform or kubectl with correct URL
```

### cert-manager `ScopeLocked` cleaning up TXT records

**Cause:** A `CanNotDelete` management lock on the DNS zone prevents cert-manager from deleting `_acme-challenge` TXT records after validation.
**Fix:** Don't use management locks on the DNS zone. The zone is protected by being in a separate resource group (`rg-kinotic-global`).

### `Insufficient memory` — pods stuck in Pending

**Cause:** Kubernetes reserves memory based on pod `requests`, not actual usage.
**Fix:** Check what's consuming reservations:
```bash
kubectl top nodes
kubectl top pods -A --sort-by=memory
kubectl get pods -A | grep Pending
kubectl describe pod <pending-pod> -n <namespace>  # check Events
```

Beta sizing assumes:
- ES data: 6 GB x 3 = 18 GB reserved
- ES master: 2 GB x 1 = 2 GB reserved
- kinotic-server: 2 GB x 2 = 4 GB reserved
- System + observability: ~5 GB
- Total: ~29 GB on 48 GB (3 x 16 GB nodes)

If tight, reduce ES data memory or kinotic-server replicas in the values.

### Loki `loki-chunks-cache` stuck Pending

**Cause:** The Loki chart deploys memcached cache pods by default. Not needed for beta.
**Fix:** Already disabled in `values-loki.yaml`:
```yaml
chunksCache:
  enabled: false
resultsCache:
  enabled: false
```

### Grafana `assertNoLeakedSecrets` error

**Cause:** The Grafana chart validates that secrets aren't in plain values. The Entra ID client secret passed via `set_sensitive` triggers this.
**Fix:** Already handled — `assertNoLeakedSecrets = false` is set in the helm values. The secret is still protected by terraform's `set_sensitive`.

### LoadBalancer service unreachable from internet (empty subnet NSG)

**Cause:** A custom NSG associated with the AKS subnet that has no explicit inbound allow rules blocks all internet traffic. Azure's default NSG behavior denies inbound from the internet. AKS creates its own NSG in the MC_ resource group with the correct LB rules, but a subnet-level NSG takes precedence.
**Symptoms:** No LoadBalancer service is reachable from outside the cluster — not kinotic-server, not Grafana. Internal cluster-to-pod connectivity works. Azure LB health probes, rules, and MC_ NSG all look correct.
**Fix:** Remove the custom NSG from the AKS subnet. AKS manages its own NSG:
```bash
# Remove NSG association from subnet
az network vnet subnet update \
  --resource-group rg-kinotic-production \
  --vnet-name vnet-kinotic-production \
  --name snet-kinotic-production-aks \
  --remove networkSecurityGroup
```
If you need a custom NSG for other reasons (e.g. restricting SSH to Firecracker VMs), add explicit inbound allow rules for the LB ports (443, 8443, 58503) and the LB health probe source (`AzureLoadBalancer` service tag). Example:
```bash
az network nsg rule create --resource-group rg-kinotic-production \
  --nsg-name nsg-kinotic-production-aks --name AllowLB \
  --priority 100 --direction Inbound --access Allow \
  --source-address-prefixes Internet --destination-port-ranges 443 8443 58503 \
  --protocol Tcp

az network nsg rule create --resource-group rg-kinotic-production \
  --nsg-name nsg-kinotic-production-aks --name AllowLBProbes \
  --priority 110 --direction Inbound --access Allow \
  --source-address-prefixes AzureLoadBalancer --destination-port-ranges '*' \
  --protocol '*'
```

### LoadBalancer service unreachable from internet (Cilium + externalTrafficPolicy: Local)

**Cause:** Cilium's eBPF dataplane on AKS doesn't correctly forward LoadBalancer traffic to local pods when `externalTrafficPolicy: Local` is set. TCP connections timeout even though NSG rules, health probes, and pod endpoints are all correct. This is a [known Cilium issue](https://github.com/cilium/cilium/issues/31333).
**Symptoms:** `curl` to the LB IP hangs/timeouts. Internal cluster-to-pod connectivity works fine. NSG shows ports allowed. LB health probes pass.
**Fix:** Use `externalTrafficPolicy: Cluster` instead. Sticky sessions still work via `sessionAffinity: ClientIP`. The trade-off is losing client IP preservation (all traffic appears from node IP). Already fixed in the service template.
**Long-term:** Revisit when Cilium fixes Local policy support on AKS, or switch to `kube-proxy` mode.

### Privileged port binding (port 443) in containers

**Cause:** Some container runtimes block binding to ports below 1024 even inside containers. Vert.x fails silently — logs show "listening on 443" but the port isn't actually open.
**Fix:** Use port 8443 internally, remap via the LoadBalancer service (`port: 443, targetPort: 8443`). Set `kinotic.webServerPort: 8443` and `service.externalPorts.ui: 443` in values.

### DNS A record `already exists`

**Cause:** DNS A record was created on a previous apply but terraform lost the state reference.
**Fix:** Import it:
```bash
terraform import azurerm_dns_a_record.api /subscriptions/<sub-id>/resourceGroups/rg-kinotic-global/providers/Microsoft.Network/dnsZones/kinotic.ai/A/api
```

### Azure eventual consistency — 404 on freshly created resources

**Cause:** Resource group exists but Azure API hasn't propagated globally yet.
**Fix:** Re-run `terraform apply`. The resource will create on retry.

### Resources exist in Azure but not in terraform state

**Cause:** Terraform created the resource but crashed before recording state.
**Fix:** Import the resource:
```bash
terraform import <resource_address> <azure_resource_id>
```

### `terraform import` fails with provider errors

**Cause:** helm/kubernetes providers need the AKS cluster to initialize, even for unrelated imports.
**Fix:** Temporarily move files with `data.kubernetes_service` aside, import, restore:
```bash
mv dns.tf dns.tf.bak
terraform import <resource_address> <azure_resource_id>
mv dns.tf.bak dns.tf
```

## Useful Commands

```bash
# Full cluster status
kubectl get pods -A
kubectl top nodes
kubectl top pods -A --sort-by=memory

# Elasticsearch
kubectl get elasticsearch -n elastic
kubectl logs -l common.k8s.elastic.co/type=elasticsearch -n elastic --tail=10

# cert-manager
kubectl describe clusterissuer letsencrypt-prod
kubectl get challenges -A
kubectl describe certificate kinotic-tls -n kinotic
kubectl logs -l app.kubernetes.io/name=cert-manager -n cert-manager --tail=20

# kinotic-server
kubectl logs -l app=kinotic -n kinotic --tail=20
curl -sk https://kinotic.ai/health/

# Grafana
kubectl logs -l app.kubernetes.io/name=grafana -n observability --tail=10

# Terraform state
terraform state list
terraform state show <resource>
```
