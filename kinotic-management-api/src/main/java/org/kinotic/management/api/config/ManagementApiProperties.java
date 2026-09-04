package org.kinotic.management.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 *
 * Created By Navíd Mitchell 🤪on 8/25/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ManagementApiProperties {

    @Valid
    private GithubProperties github = new GithubProperties();

    /**
     * Base URL of the Loki HTTP API the {@code LogService} reads workload logs from; override per
     * environment via {@code kinotic.managementApi.lokiUrl} (env {@code KINOTIC_MANAGEMENTAPI_LOKIURL}).
     */
    @NotBlank
    private String lokiUrl = "http://localhost:3100";

    /**
     * Base URL of the Tempo HTTP API the {@code TelemetryService} searches traces in; override per
     * environment via {@code kinotic.managementApi.tempoUrl} (env {@code KINOTIC_MANAGEMENTAPI_TEMPOURL}).
     */
    @NotBlank
    private String tempoUrl = "http://localhost:3200";

    /**
     * Base URL of the Mimir HTTP API the {@code TelemetryService} queries metrics from, whose
     * Prometheus API is served under {@code /prometheus}; override per environment via
     * {@code kinotic.managementApi.mimirUrl} (env {@code KINOTIC_MANAGEMENTAPI_MIMIRURL}).
     */
    @NotBlank
    private String mimirUrl = "http://localhost:9009";

}
