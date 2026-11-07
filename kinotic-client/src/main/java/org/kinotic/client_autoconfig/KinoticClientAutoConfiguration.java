package org.kinotic.client_autoconfig;

import io.vertx.core.Vertx;
import org.kinotic.client.api.KinoticConnectionFactory;
import org.kinotic.client.internal.api.DefaultKinoticConnectionFactory;
import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core_autoconfig.KinoticCoreAutoConfiguration;
import org.kinotic.core_autoconfig.KinoticRpcProxyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configures the Java client in a Spring Boot application: a Vert.x instance unless the application has one,
 * and the {@link KinoticConnectionFactory} the application connects to gateways with, over the beans a service
 * proxy is built from. It is defined in a separate package because it must not be scanned by the Spring context.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@AutoConfiguration(after = {KinoticRpcProxyAutoConfiguration.class, KinoticCoreAutoConfiguration.class})
public class KinoticClientAutoConfiguration {

    // a cluster member runs the clustered instance of its core; an application outside the cluster gets one of its own
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    public KinoticConnectionFactory kinoticConnectionFactory(Vertx vertx, RpcServiceProxyFactory proxyFactory, JsonMapper jsonMapper) {
        return new DefaultKinoticConnectionFactory(vertx, proxyFactory, jsonMapper);
    }

}
