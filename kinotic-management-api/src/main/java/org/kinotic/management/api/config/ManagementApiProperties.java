package org.kinotic.management.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /**
     * Where projects' repositories are hosted.
     */
    @NotNull
    private RepoHostType repoHost = RepoHostType.GITHUB;

    @Valid
    private GithubProperties github = new GithubProperties();

    @Valid
    private EntityDefinitionProperties entityDefinition = new EntityDefinitionProperties();

    /**
     * Base URL of the management server's API as a browser reaches it (no trailing slash): the base of the
     * management server's OIDC {@code redirect_uri}s, including the callback of each organization's own OIDC
     * configuration, and its OAuth issuer unless {@code kinotic.managementServer.issuerBaseUrl} is set.
     */
    private String apiBaseUrl;

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

    /**
     * Leaves out the MCP server on {@code POST /mcp}, for a server that runs management-api's services without an
     * API gateway.
     */
    private boolean disableMcpServer = false;

}
