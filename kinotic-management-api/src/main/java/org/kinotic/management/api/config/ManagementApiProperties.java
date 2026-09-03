package org.kinotic.management.api.config;

import jakarta.validation.Valid;
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
     * Loki, which backs the {@code LogService}.
     */
    @Valid
    private GrafanaBackendProperties loki = new GrafanaBackendProperties().setUrl("http://localhost:3100");

    /**
     * Tempo, which backs the {@code TelemetryService}'s trace queries.
     */
    @Valid
    private GrafanaBackendProperties tempo = new GrafanaBackendProperties().setUrl("http://localhost:3200");

    /**
     * Mimir, whose Prometheus API under {@code /prometheus} backs the {@code TelemetryService}'s
     * metric queries.
     */
    @Valid
    private GrafanaBackendProperties mimir = new GrafanaBackendProperties().setUrl("http://localhost:9009");

}
