# Production Readiness

Status and next steps for the Azure deployment. Items are ordered by priority within each tier.

## Must Have (Beta) — All Complete

- [x] AKS cluster with Azure RBAC, OIDC issuer, workload identity
- [x] Elasticsearch via ECK (1 master + 3 data in beta, 3 master + 3 data in production)
- [x] Kinotic server (3 replicas, TLS, production Spring profile, sticky sessions)
- [x] TLS via cert-manager + Let's Encrypt (DNS-01 via Azure DNS, auto-renewal, Reloader)
- [x] Azure DNS zone for kinotic.ai with dynamic A records from LB IPs
- [x] Consistent namespace layout (elastic-system, elastic, kinotic, observability)
- [x] Remote terraform state backend — `bootstrap-state.sh` + Azure Storage
- [x] ES plain HTTP internally (cluster-internal only)
- [x] Pinned image version — `kinotic_version` variable
- [x] CORS restricted to kinotic.ai
- [x] ES credential sync across namespaces — `es-secret-sync` Helm chart with RBAC and retry
- [x] NetworkPolicy on ES — allows traffic from kinotic + elastic-system only
- [x] `beta_mode` toggle — single command to scale from beta to production topology
- [x] KinD parity — same namespace layout, charts, TLS, NetworkPolicy, secret sync
- [x] Load generator for sample data
- [x] Observability — Loki (log storage, Azure Blob backend), Alloy (DaemonSet log collector), Grafana (dashboards)
- [x] Grafana Entra ID login — auto-provisioned App Registration, on by default for Azure
- [x] Grafana TLS — uses same Let's Encrypt wildcard cert, accessible at `https://grafana.kinotic.ai`
- [x] Cost documentation — see [COST.md](COST.md)

## Must Have (Production)

- [ ] Azure Container Registry (ACR) with managed identity pull from AKS — eliminates Docker Hub rate limits and public dependency
- [ ] Automated es-secret-sync on credential rotation — currently runs at deploy time only. Options: CronJob on a schedule, or a controller that watches the source secret for changes
- [ ] ES snapshots to Azure Blob Storage — without this, data loss on ES is unrecoverable. ECK supports `secureSettings` with an Azure snapshot repository
- [ ] OIDC / identity provider for kinotic-server — Keycloak is only wired for KinD. Production needs Azure AD, Auth0, or a managed Keycloak. Includes CORS, redirect URIs, and audience configuration
- [ ] CI/CD pipeline — automated image builds, terraform plan on PR, terraform apply on merge, image promotion between environments
- [ ] Load generator bearer auth support — currently uses basic auth only, cannot run after OIDC is enabled as the sole auth provider
- [ ] Grafana alerting rules — configure alerts for ES cluster health, pod restarts, error rate spikes, disk pressure

## Should Have

- [ ] Metrics collection via Alloy — extend the Alloy pipeline to scrape Prometheus metrics from kinotic-server, ECK operator, and ES. Ship to a Prometheus-compatible backend or Grafana Mimir
- [ ] Horizontal Pod Autoscaler (HPA) for kinotic-server — fixed replica count of 3 is fine for beta, production traffic needs scaling based on CPU/memory or custom metrics
- [ ] ES node pool autoscaler — when `beta_mode=false`, the ES node pool has `ignore_changes = [node_count]` but no autoscaler config. Add `auto_scaling_enabled`, `min_count`, `max_count`
- [ ] Firecracker NSG lockdown — SSH currently allowed from anywhere. Restrict to known IPs or deploy an Azure Bastion
- [ ] Loki retention tuning — adjust retention period and compaction based on actual log volume and query patterns

## Nice to Have

- [ ] Azure Key Vault integration — secrets are in K8s secrets only. Key Vault adds rotation, audit logging, and centralized RBAC
- [ ] WAF / DDoS protection — Azure LB is L4 only. Azure Front Door or Application Gateway adds WAF rules
- [ ] Multi-environment promotion — staging environment with separate AKS cluster sharing the same terraform modules but different tfvars
- [ ] Pod Security Standards — enforce restricted pod security at the namespace level to prevent privileged containers (except ES sysctl init containers)
- [ ] Cost optimization — reserved instances for ES node pool VMs, spot instances for non-critical workloads, right-sizing based on actual usage metrics
- [ ] Grafana dashboard provisioning — pre-built dashboards for kinotic-server, ES health, and Alloy pipeline metrics deployed via ConfigMap
