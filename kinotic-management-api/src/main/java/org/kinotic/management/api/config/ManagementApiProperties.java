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
     * Loki configuration for the {@code LogService}.
     */
    @Valid
    private LokiProperties loki = new LokiProperties();

    /**
     * Tempo configuration for the {@code TelemetryService}'s trace queries.
     */
    @Valid
    private TempoProperties tempo = new TempoProperties();

    /**
     * Mimir configuration for the {@code TelemetryService}'s metric queries.
     */
    @Valid
    private MimirProperties mimir = new MimirProperties();

}
