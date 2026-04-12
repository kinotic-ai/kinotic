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

}
