package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for reaching the Loki instance that backs the {@code LogService}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "kinotic.loki")
public class LokiProperties {

    /**
     * Base URL of the Loki HTTP API (e.g. {@code http://loki:3100}). Defaults to a local instance;
     * override per environment via {@code kinotic.loki.url} (env {@code KINOTIC_LOKI_URL}).
     */
    private String url = "http://localhost:3100";
}
