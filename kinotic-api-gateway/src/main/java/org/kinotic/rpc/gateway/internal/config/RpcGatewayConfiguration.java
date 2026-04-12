package org.kinotic.rpc.gateway.internal.config;

import org.kinotic.rpc.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.rpc.gateway.api.config.ApiGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Created By Navíd Mitchell 🤪on 3/9/26
 */
@Configuration
public class RpcGatewayConfiguration {

    @Bean
    public ApiGatewayProperties rpcGatewayProperties(KinoticApiGatewayProperties kinoticProperties){
        return kinoticProperties.getRpcGateway();
    }

}
