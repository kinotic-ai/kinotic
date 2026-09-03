package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where one of the Grafana backends the server reads from — Loki, Tempo, Mimir — is reached.
 * Bound under {@link ManagementApiProperties} as {@code kinotic.managementApi.<backend>.*}, with
 * the backend's default there.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GrafanaBackendProperties {

    /**
     * Base URL of the backend's HTTP API (e.g. {@code http://loki:3100}); override per environment
     * via {@code kinotic.managementApi.<backend>.url} (env {@code KINOTIC_MANAGEMENTAPI_<BACKEND>_URL}).
     */
    private String url;
}
