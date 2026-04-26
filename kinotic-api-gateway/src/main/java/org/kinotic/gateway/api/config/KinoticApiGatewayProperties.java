package org.kinotic.gateway.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 *
 * Created By Navíd Mitchell 🤪on 3/9/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticApiGatewayProperties extends KinoticProperties {

    /**
     * If true, RPC gateway functionality will not be loaded.
     */
    private boolean disableRpcGateway = false;

    /**
     * RPC gateway properties configuration
     */
    private ApiGatewayProperties rpcGateway = new ApiGatewayProperties(this);

    /**
     * OIDC login flow configuration.
     */
    private OidcLoginProperties login = new OidcLoginProperties();

    /**
     * Static-file web server configuration. Disabled in KinD/Azure where the SPA
     * is hosted outside the cluster.
     */
    private WebServerProperties webServer = new WebServerProperties();

    /**
     * CORS settings applied to the api-gateway router (port 58503).
     */
    private CorsProperties cors = new CorsProperties();

    /**
     * Path the health-check endpoint is mounted at on the api-gateway router.
     */
    private String healthCheckPath = "/health";

}
