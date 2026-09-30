package org.kinotic.appserver.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.regex.Pattern;

/** The URLs the app server is reached at, {@code kinotic.appServer.*}, set per deployment. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "kinotic.appServer")
public class AppServerProperties {

    /**
     * Base URL every application's API host is a label under (scheme + domain + optional port, no trailing
     * slash): with {@code https://apps-api.kinotic.ai}, application {@code orders} of organization {@code acme}
     * is served at {@code https://acme--orders.apps-api.kinotic.ai}, which is also its OAuth issuer.
     */
    @NotBlank
    private String apiBaseUrl;

    /**
     * Origins admitted as a UI of every application, for UIs a developer serves from their own machine, such as
     * {@code http://localhost:\d+}. When unset, an application's login is made only from its published sites.
     */
    private Pattern localUiOriginPattern;
}
