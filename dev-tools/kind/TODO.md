# KinD Cluster TODO

## Pending Services to Deploy

### Observability Stack

These services have port mappings configured in `kind-config.yaml` but are not yet deployed:

- [ ] **Grafana** (localhost:3000 → nodePort:30300)
  - Dashboard visualization for metrics and logs
  - Use existing dashboard configs from `docker-compose/grafana-dashboards/`
  - Reference: `docker-compose/grafana-datasource.yaml`

- [ ] **Prometheus** (localhost:9080 → nodePort:30081)
  - Metrics collection and alerting
  - Reference: `docker-compose/prometheus.yml`

- [ ] **Jaeger** (localhost:16686 → nodePort:30686)
  - Distributed tracing UI
  - Reference: `docker-compose/compose-otel.yml`

### Implementation Notes

1. Add deployment functions to `lib/deploy.sh`:
   - `deploy_grafana()`
   - `deploy_prometheus()`
   - `deploy_jaeger()`

2. Consider using the kube-prometheus-stack Helm chart for Prometheus + Grafana together

3. OpenTelemetry collector may be needed for Jaeger integration
   - Reference: `docker-compose/otel-collector-config.yaml`

4. Update `deploy_dependencies()` to optionally include observability stack

### Related Files

- `docker-compose/compose-otel.yml` - OpenTelemetry configuration
- `docker-compose/grafana-dashboards/` - Pre-built dashboards
- `docker-compose/prometheus.yml` - Prometheus scrape config
- `docker-compose/otel-collector-config.yaml` - OTEL collector config

## Completed

- [x] Elasticsearch (localhost:9200)
- [x] PostgreSQL (localhost:5555)
- [x] Keycloak (localhost:8888)
- [x] Structures Server (all ports exposed)
- [x] Kubernetes RBAC for Ignite clustering

