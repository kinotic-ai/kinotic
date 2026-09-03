package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for reaching the Tempo instance that backs the {@code TelemetryService}'s trace
 * queries. Bound under {@link ManagementApiProperties} as {@code kinotic.managementApi.tempo.*}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TempoProperties {

    /**
     * Base URL of the Tempo HTTP API (e.g. {@code http://tempo:3200}). Defaults to a local instance;
     * override per environment via {@code kinotic.managementApi.tempo.url} (env {@code KINOTIC_MANAGEMENTAPI_TEMPO_URL}).
     */
    private String url = "http://localhost:3200";
}
