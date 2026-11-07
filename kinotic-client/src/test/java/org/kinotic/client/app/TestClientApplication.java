package org.kinotic.client.app;

import org.kinotic.client.gateway.Circle;
import org.kinotic.client.gateway.Shape;
import org.kinotic.core_autoconfig.KinoticCoreAutoConfiguration;
import org.kinotic.gateway_autoconfig.KinoticApiGatewayAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;

/**
 * The application the client tests run in: a Spring Boot context with the client auto-configured and nothing of the
 * cluster or the gateway, as an application that only calls a gateway has, plus the Jackson customization a
 * {@link Shape} reply needs.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@SpringBootApplication(exclude = {KinoticCoreAutoConfiguration.class, KinoticApiGatewayAutoConfiguration.class, JmxAutoConfiguration.class})
public class TestClientApplication {

    @Bean
    public JsonMapperBuilderCustomizer shapes() {
        return builder -> builder.addModule(new SimpleModule().addAbstractTypeMapping(Shape.class, Circle.class));
    }

    public static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(TestClientApplication.class)
                .properties("spring.main.banner-mode=off", "spring.main.web-application-type=none")
                .run();
    }

}
