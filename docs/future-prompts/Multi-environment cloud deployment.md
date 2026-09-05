# Multi-environment cloud deployment (Kinotic Cloud)

**Status: BLOCKED — do not start until Navid confirms the infra budget is secured.**

Companion to `docs/future-prompts/Multi-environment architecture.md` (the code-side plan). That
plan builds all capability against Testcontainers and an unchanged all-in-one local compose;
this plan is the Terraform/Helm work that actually deploys the multi-environment topology to
Kinotic Cloud, plus the production cutover. Prerequisite: the code plan complete through Phase 7
(Phase 9 Promotion can land before or after — promotion is inert until a second environment
exists).

## Scope

1. **Per-environment Elasticsearch clusters** — one eck-stack release per environment
   (`values-<env>.yaml` overlays on `deployment/helm/eck-stack`), alongside the existing OS
   cluster. Provision **two users per env cluster** (code plan, Phase 5): the gateway data user
   (document CRUD on entity indices, no index management) and the OS admin user
   (index/template/mapping management, no document read/write).
2. **Gateway releases** — parameterize the existing `deployment/helm/kinotic` chart by profile
   rather than cloning a second chart: one release per environment activating `app-gateway`
   with `KINOTIC_APPLICATIONGATEWAY_*` env vars (environmentId, env-cluster connection); the OS
   release activates `os-server`. Single image `kinoticai/kinotic-server` everywhere.
3. **Ignite bus isolation** — infrastructure-level, no app config (owner decision): each
   release gets its own Ignite discovery Service (the chart already templates one via
   `kinotic.cluster.*`); Ignite's Kubernetes-topology discovery resolves membership to whatever
   that Service selects. Terraform places production on a **dedicated node pool or a separate
   k8s cluster**.
4. **NetworkPolicy** — env ES reachable from its gateway namespace (data user) and the kinotic
   namespace (OS admin user only); OS ES reachable from the kinotic and gateway namespaces
   (gateways read OS metadata / write scoped IAM indices).
5. **Environment records** — create/update the `Environment` documents (`gatewayUrl`, status)
   for the deployed environments, and the OS server's `kinotic.environmentClusters.<envId>.*`
   connection properties, as part of the rollout.
6. **Cutover** (the one deliberate breaking step, only after the frontend talks to gateways for
   entity data): in `application-os-server.yml`, set `disablePersistence: true` and drop
   `app-api` from `kinotic.zones`. From then on the OS bus carries no entity data plane, and
   the zones intersection automatically narrows organization participants on the OS server to
   `management-api.**`.
7. **Secrets/TLS** — extend the `platformSecrets` mounting (JWT signing keys are shared across
   OS server and gateways — same Key Vault objects), per-gateway TLS, and the es-secret-sync
   pattern for the new env clusters.

## Non-goals

- No changes to local docker-compose or the IDE workflow — local stays single-ES, single
  all-in-one server, `development` environment only (code plan, Phase 8).
- No new container images and no new Helm chart — one image, one chart, parameterized.

## Open items to resolve when unblocked

- Dedicated node pool vs separate k8s cluster for production (cost/failure-domain trade).
- Which environments exist at launch beyond `development`/`production`, if any.
- Public DNS/ingress shape for per-environment gateway URLs (feeds `Environment.gatewayUrl`).
