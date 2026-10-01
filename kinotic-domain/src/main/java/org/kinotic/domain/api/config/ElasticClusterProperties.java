package org.kinotic.domain.api.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.util.List;

/**
 * How to connect to one Elasticsearch cluster: its nodes, the credentials to send, and the timeouts to apply.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ElasticClusterProperties {

    @NotEmpty
    private List<ElasticConnectionInfo> connections = List.of(new ElasticConnectionInfo());

    private String username = null;

    private String password = null;

    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration socketTimeout = Duration.ofMinutes(1);

    public boolean hasUsernameAndPassword(){
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

}
