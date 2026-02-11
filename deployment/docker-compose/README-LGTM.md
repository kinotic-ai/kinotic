# LGTM Stack Quick Start Guide

## Starting the Services

From the `docker-compose` directory, run:

```bash
docker compose up -d
```

This will start all services including:
- **Grafana** (port 3000) - Main observability UI
- **Tempo** (port 3200) - Traces storage
- **Loki** (port 3100) - Logs storage
- **Mimir** (port 9009) - Metrics storage
- **OpenTelemetry Collector** (ports 4317, 4318) - Telemetry collection
- **Structures Server** - Your application
- **Elasticsearch/Kibana** - Application data storage

## Accessing Grafana

1. Open your browser and navigate to:
   ```
   http://localhost:3000
   ```

2. **No login required** - Anonymous access is enabled with Admin role

3. **Using Grafana Drilldown Apps (Queryless Experience):**
   
   Grafana provides built-in Drilldown apps that offer a queryless, point-and-click interface for exploring your observability data. Access them from the Grafana navigation menu:
   
   - **Metrics Drilldown** (GA) - Browse Prometheus-compatible metrics from Mimir without writing queries
     - Search and select metric names
     - Automatic optimal visualization (gauge, counter, histogram)
     - Break down metrics by labels (namespace, cluster, service, etc.)
     - View related metrics for comprehensive understanding
   
   - **Logs Drilldown** (GA) - Explore logs from Loki with an intuitive interface
     - Filter logs with point-and-click interactions
     - Visualize log volumes to detect anomalies
     - Identify patterns and filter noise
     - First-class support for OpenTelemetry resource attributes
   
   - **Traces Drilldown** (Public Preview) - Analyze traces from Tempo
     - Compare workflow to identify problematic attributes
     - View RED metrics (Rate, Errors, Duration) for performance issues
     - Automatic trace comparison to spot anomalies
     - Visualize related issues over time without TraceQL complexity
   
   These apps provide a seamless, queryless experience for exploring metrics, logs, and traces stored in your LGTM stack.

## Verifying Services

Check service status:
```bash
docker compose ps
```

View logs:
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f grafana
docker compose logs -f tempo
docker compose logs -f mimir
docker compose logs -f otel-collector
```

## Stopping Services

```bash
docker compose down
```

To also remove volumes (clears all data):
```bash
docker compose down -v
```

## Service URLs

- **Grafana**: http://localhost:3000
- **Tempo UI**: http://localhost:3200
- **Loki**: http://localhost:3100
- **Mimir**: http://localhost:9009
- **Structures Server UI**: http://localhost:9090
- **Structures Server GraphQL**: http://localhost:4000
- **Structures Server OpenAPI**: http://localhost:8080

## Troubleshooting

If services don't start:
1. Check logs: `docker compose logs <service-name>`
2. Verify ports aren't already in use
3. Ensure Docker has enough resources (memory/CPU)
4. Check disk space for volume storage

## Data Persistence

- Tempo data: Stored in `tempo-data` Docker volume
- Mimir data: Stored in `mimir-data` Docker volume
- Elasticsearch data: Stored in `~/structures/elastic-data` (host path)

