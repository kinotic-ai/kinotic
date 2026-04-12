# Production Readiness

Status and next steps for the Azure deployment. Items are ordered by priority within each tier.

## Must Have (Beta) — All Complete

- [x] AKS cluster with Azure RBAC, OIDC issuer, workload identity
- [x] Elasticsearch via ECK (1 master + 3 data in beta, 3 master + 3 data in production)
- [x] Kinotic server (2 replicas beta / 3 production, TLS, STOMP only on LB port 443)
- [x] TLS via cert-manager + Let's Encrypt (DNS-01 via Azure DNS, auto-renewal, Reloader)
- [x] Azure DNS zone for kinotic.ai with A record for api.kinotic.ai
- [x] Static Web App for SPA (portal.kinotic.ai, free SSL, CDN)
- [x] Consistent namespace layout (elastic-system, elastic, kinotic, observability)
- [x] Remote terraform state backend — `bootstrap-state.sh` + Azure Storage
- [x] Terraform split: global (permanent) / cluster (disposable) / frontend (independent)
- [x] ES plain HTTP internally (cluster-internal only)
- [x] Pinned image version — `kinotic_version` variable
- [x] CORS restricted to portal.kinotic.ai
- [x] ES credential sync across namespaces — `es-secret-sync` Helm chart with RBAC and retry
- [x] NetworkPolicy on ES — allows traffic from kinotic + elastic-system only
- [x] `beta_mode` toggle — single command to scale from beta to production topology
- [x] KinD parity — same namespace layout, charts, TLS, NetworkPolicy, secret sync
- [x] Load generator for sample data
- [x] Observability — Loki (log storage), Alloy (DaemonSet log collector), Grafana (dashboards, port-forward only)
- [x] Grafana Entra ID login — auto-provisioned App Registration, on by default
- [x] Azure Key Vault — kinotic-server accesses via workload identity (Secrets Officer role)
- [x] Azure Communication Services — email via workload identity, custom domain (kinotic.ai) with automated DNS verification
- [x] Cost documentation — see [COST.md](COST.md)

## Must Have (Production)

- [ ] Azure Container Registry (ACR) with managed identity pull from AKS — eliminates Docker Hub rate limits and public dependency
- [ ] Automated es-secret-sync on credential rotation — currently runs at deploy time only. Options: CronJob on a schedule, or a controller that watches the source secret for changes
- [ ] ES snapshots to Azure Blob Storage — without this, data loss on ES is unrecoverable. ECK supports `secureSettings` with an Azure snapshot repository
- [ ] OIDC / identity provider for kinotic-server — Entra ID App Registration is provisioned, needs to be wired into kinotic-server Spring config
- [ ] CI/CD pipeline — automated image builds, terraform plan on PR, terraform apply on merge, image promotion between environments
- [ ] Load generator bearer auth support — currently uses basic auth only, cannot run after OIDC is enabled as the sole auth provider
- [ ] Grafana alerting rules — configure alerts for ES cluster health, pod restarts, error rate spikes, disk pressure

## Should Have

- [ ] Metrics collection via Alloy — extend the Alloy pipeline to scrape Prometheus metrics from kinotic-server, ECK operator, and ES
- [ ] Horizontal Pod Autoscaler (HPA) for kinotic-server — fixed replica count is fine for beta, production traffic needs scaling
- [ ] ES node pool autoscaler — when `beta_mode=false`, add `auto_scaling_enabled`, `min_count`, `max_count`
- [ ] Firecracker NSG lockdown — SSH currently allowed from anywhere. Restrict to known IPs or deploy an Azure Bastion
- [ ] Loki retention tuning — adjust retention period and compaction based on actual log volume

## Nice to Have

- [ ] WAF / DDoS protection — Azure Front Door or Application Gateway adds WAF rules
- [ ] Multi-environment promotion — staging with separate cluster sharing the same terraform modules but different tfvars
- [ ] Pod Security Standards — enforce restricted pod security at the namespace level
- [ ] Cost optimization — reserved instances, spot instances, right-sizing based on actual usage
- [ ] Grafana dashboard provisioning — pre-built dashboards deployed via ConfigMap
- [ ] Cilium externalTrafficPolicy: Local — revisit when Cilium fixes Local policy support on AKS
