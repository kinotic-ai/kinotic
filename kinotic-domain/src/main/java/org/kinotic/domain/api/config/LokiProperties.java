package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for reaching the Loki instance that backs the {@code LogService}.
 * Bound under {@link DomainProperties} as {@code kinotic.domain.loki.*}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LokiProperties {

    /**
     * Base URL of the Loki HTTP API (e.g. {@code http://loki:3100}). Defaults to a local instance;
     * override per environment via {@code kinotic.domain.loki.url} (env {@code KINOTIC_DOMAIN_LOKI_URL}).
     */
    private String url = "http://localhost:3100";
}
