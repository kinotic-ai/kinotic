package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for reaching the Mimir instance that backs the {@code TelemetryService}'s metric
 * queries. Bound under {@link ManagementApiProperties} as {@code kinotic.managementApi.mimir.*}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class MimirProperties {

    /**
     * Base URL of the Mimir HTTP API (e.g. {@code http://mimir:9009}), under which the Prometheus
     * query API is served at {@code /prometheus}. Defaults to a local instance; override per
     * environment via {@code kinotic.managementApi.mimir.url} (env {@code KINOTIC_MANAGEMENTAPI_MIMIR_URL}).
     */
    private String url = "http://localhost:9009";
}
