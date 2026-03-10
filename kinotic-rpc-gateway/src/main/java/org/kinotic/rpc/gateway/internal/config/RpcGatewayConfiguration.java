package org.kinotic.rpc.gateway.internal.config;

import org.kinotic.rpc.gateway.api.config.KinoticRpcGatewayProperties;
import org.kinotic.rpc.gateway.api.config.RpcGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Created By Navíd Mitchell 🤪on 3/9/26
 */
@Configuration
public class RpcGatewayConfiguration {

    @Bean
    public RpcGatewayProperties rpcGatewayProperties(KinoticRpcGatewayProperties kinoticProperties){
        return kinoticProperties.getRpcGateway();
    }

}
