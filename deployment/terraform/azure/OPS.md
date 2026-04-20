# Azure Operations — Day 2

Ongoing operations, scaling, and maintenance for the Azure deployment.
For first-time setup, see [README.md](README.md).

## Scaling

**Beta to production topology:**
```bash
# In terraform.tfvars:
beta_mode = false
terraform apply
```
Adds dedicated ES node pool (3x Standard_E8s_v5), ECK migrates ES pods, system pool gets tainted. No storage migration — both tiers use Premium ZRS.

**Expand ES storage:**
Increase `storage:` in the eck-stack values overlay, then `terraform apply`. ECK expands PVCs online with zero downtime. You can only grow, never shrink.

**Scale kinotic-server replicas:**
Change `replicaCount` in `config/kinotic-server/values.yaml`, then `terraform apply`.

**Scale ES data nodes:**
Change `count:` in the eck-stack values overlay, then `terraform apply`. ECK rebalances shards automatically.

**Add Firecracker hosts:**
```bash
terraform apply -var="enable_firecracker=true" -var="firecracker_node_count=3"
```

## Upgrades

**Kinotic server version:**
1. Push new image to Docker Hub
2. Update `kinotic_version` in `terraform.tfvars`
3. `terraform apply`

**Elasticsearch version:**
Bump `version:` in `deployment/helm/eck-stack/values.yaml`, then `terraform apply`. ECK rolls nodes one at a time.

**Kubernetes version:**
Update `kubernetes_version` in `terraform.tfvars`, then `terraform apply`. AKS upgrades nodes with surge strategy.

## Credentials

**ES password rotation:**
Re-run the ES secret sync to copy the updated secret:
```bash
helm upgrade es-secret-sync ../../helm/es-secret-sync -n kinotic
kubectl rollout restart deployment kinotic-server -n kinotic
```

**Grafana Entra ID:**
Managed by terraform — `terraform apply` refreshes the app registration and client secret.

**User tenant assignment:**
```bash
terraform output set_user_tenant_id
# Follow the command to set a user's kinoticTenantId
```

## Monitoring

**Grafana:** port-forward only (no external LB)
```bash
kubectl port-forward svc/grafana -n observability 3000:3000
# Then open http://localhost:3000 (Entra ID login)
```

**Useful Loki queries:**
```
{namespace="kinotic"}                          # All kinotic-server logs
{namespace="kinotic"} | json | level="ERROR"   # Errors only
{namespace="elastic"}                          # Elasticsearch logs
{app="kinotic-server"} | json | level="WARN"   # Warnings
```

**kubectl:**
```bash
kubectl get pods -A
kubectl top nodes
kubectl top pods -A --sort-by=memory
kubectl get elasticsearch -n elastic
kubectl describe certificate kinotic-tls -n kinotic
```

## Rebuild Without Touching DNS

To destroy and recreate everything except the DNS zone:

```bash
# 1. Remove DNS zone from state
terraform state rm azurerm_dns_zone.main

# 2. Destroy everything else
terraform destroy

# 3. Follow the deploy-day guide (README.md) steps 5-6
#    The DNS zone persists in rg-kinotic-global

# 4. After AKS is up, import the DNS zone back
terraform import azurerm_dns_zone.main \
  /subscriptions/<sub-id>/resourceGroups/rg-kinotic-global/providers/Microsoft.Network/dnsZones/kinotic.ai

# 5. Full apply
terraform apply
```

**Note:** The `terraform import` step requires AKS to exist (helm/kubernetes providers
need a cluster). If import fails with provider errors, temporarily move `dns.tf` aside,
import, then restore it.

## Certificate Renewal

cert-manager auto-renews ~30 days before expiry. Reloader detects the secret change
and triggers a rolling restart of kinotic-server.

If renewal fails, check:
```bash
kubectl describe clusterissuer letsencrypt-prod
kubectl get challenges -A
kubectl logs -l app.kubernetes.io/name=cert-manager -n cert-manager --tail=20
```

## Disaster Recovery

| Scenario | Recovery |
|---|---|
| Pod crash | Kubernetes auto-restarts. Check `kubectl describe pod` |
| Node failure | AKS auto-repairs. ES PDB protects quorum |
| AKS cluster loss | Rebuild via README.md steps 5-6. ES data on PVCs persists if PVs are Retain policy |
| DNS zone deletion | Recreate zone, re-point NS at registrar. Same zone name = same NS records |
| Cert expired | Delete certificate + challenges, re-apply via terraform |
| State corruption | Re-import resources from Azure |
