package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.net.URI;
import java.util.List;

/** Configures the external authorization service and operator bootstrap identities. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kinotic.authorization")
public class OpenFgaProperties {
    private boolean enabled;
    private URI endpoint = URI.create("http://localhost:8180");
    private String apiToken;
    private Duration checkTimeout = Duration.ofMillis(50);
    private Duration publicationTimeout = Duration.ofSeconds(10);
    private List<String> bootstrapSystemIdentityIds = List.of();
}
