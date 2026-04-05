# Production Readiness

Status and next steps for the Azure deployment. Items are ordered by priority within each tier.

## Must Have (Beta) — All Complete

- [x] AKS cluster with Azure RBAC, OIDC issuer, workload identity
- [x] Elasticsearch via ECK (single node in beta, dedicated master+data in production)
- [x] Kinotic server (3 replicas, TLS, production Spring profile, sticky sessions)
- [x] TLS via cert-manager + Let's Encrypt (DNS-01 via Azure DNS, auto-renewal, Reloader)
- [x] Azure DNS zone for kinotic.ai with dynamic A records from LB IP
- [x] Consistent namespace layout (elastic-system, elastic, kinotic)
- [x] Remote terraform state backend — `bootstrap-state.sh` + Azure Storage
- [x] ES plain HTTP internally (cluster-internal only)
- [x] Pinned image version — `kinotic_version` variable
- [x] CORS restricted to kinotic.ai
- [x] ES credential sync across namespaces — `es-secret-sync` Helm chart with RBAC and retry
- [x] NetworkPolicy on ES — allows traffic from kinotic + elastic-system only
- [x] `beta_mode` toggle — single command to scale from beta to production topology
- [x] KinD parity — same namespace layout, charts, TLS, NetworkPolicy, secret sync
- [x] Load generator for sample data
- [x] Cost documentation — see [COST.md](COST.md)

## Must Have (Production)

- [ ] Azure Container Registry (ACR) with managed identity pull from AKS — eliminates Docker Hub rate limits and public dependency
- [ ] Automated es-secret-sync on credential rotation — currently runs at deploy time only. Options: CronJob on a schedule, or a controller that watches the source secret for changes
- [ ] ES snapshots to Azure Blob Storage — without this, data loss on ES is unrecoverable. ECK supports `secureSettings` with an Azure snapshot repository
- [ ] OIDC / identity provider for production — Keycloak is only wired for KinD. Production needs Azure AD, Auth0, or a managed Keycloak. Includes CORS, redirect URIs, and audience configuration
- [ ] CI/CD pipeline — automated image builds, terraform plan on PR, terraform apply on merge, image promotion between environments
- [ ] Load generator bearer auth support — currently uses basic auth only, cannot run after OIDC is enabled as the sole auth provider

## Should Have

- [ ] Monitoring and alerting — Prometheus + Grafana or Azure Monitor. ECK operator exposes metrics on port 9090. Enable ServiceMonitor when kube-prometheus-stack is deployed. Alert on ES cluster health, pod restarts, memory pressure, disk usage
- [ ] Horizontal Pod Autoscaler (HPA) for kinotic-server — fixed replica count of 3 is fine for beta, production traffic needs scaling based on CPU/memory or custom metrics
- [ ] ES node pool autoscaler — when `beta_mode=false`, the ES node pool has `ignore_changes = [node_count]` but no autoscaler config. Add `auto_scaling_enabled`, `min_count`, `max_count`
- [ ] Firecracker NSG lockdown — SSH currently allowed from anywhere. Restrict to known IPs or deploy an Azure Bastion

## Nice to Have

- [ ] Azure Key Vault integration — secrets are in K8s secrets only. Key Vault adds rotation, audit logging, and centralized RBAC
- [ ] WAF / DDoS protection — Azure LB is L4 only. Azure Front Door or Application Gateway adds WAF rules
- [ ] Multi-environment promotion — staging environment with separate AKS cluster sharing the same terraform modules but different tfvars
- [ ] Pod Security Standards — enforce restricted pod security at the namespace level to prevent privileged containers (except ES sysctl init containers)
- [ ] Cost optimization — reserved instances for ES node pool VMs, spot instances for non-critical workloads, right-sizing based on actual usage metrics
