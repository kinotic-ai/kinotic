package org.kinotic.client_test.app;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.kinotic.client_test.gateway.Circle;
import org.kinotic.client_test.gateway.TestGatewayApplication;
import org.kinotic.client_test.gateway.Shape;
import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.gateway_autoconfig.KinoticApiGatewayAutoConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The application the client tests run in: a Spring Boot context with the client auto-configured on a Kinotic node of
 * its own, as any application using the client is, without the gateway, plus the Jackson customization a
 * {@link Shape} reply needs. Ignite discovers on free ports and works in a directory of this node's own, so it never
 * joins the test gateway's node or any other.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@EnableKinotic
@SpringBootApplication(exclude = {KinoticApiGatewayAutoConfiguration.class, JmxAutoConfiguration.class})
public class TestClientApplication {

    @Bean
    public JsonMapperBuilderCustomizer shapes() {
        return builder -> builder.addModule(new SimpleModule().addAbstractTypeMapping(Shape.class, Circle.class));
    }

    // Ignite runs one unnamed instance per JVM, which the test gateway's node is, so this node's instance is named
    @Bean
    static BeanPostProcessor igniteInstanceName() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof IgniteConfiguration configuration) {
                    configuration.setIgniteInstanceName("client");
                }
                return bean;
            }
        };
    }

    public static ConfigurableApplicationContext start() throws IOException {
        return new SpringApplicationBuilder(TestClientApplication.class)
                .properties("kinotic.ignite.discoveryType=LOCAL",
                            "kinotic.ignite.discoveryPort=" + TestGatewayApplication.freePort(),
                            "kinotic.ignite.communicationPort=" + TestGatewayApplication.freePort(),
                            "kinotic.ignite.workDirectory=" + Files.createTempDirectory(Files.createDirectories(Path.of("build", "ignite").toAbsolutePath()), "client-"),
                            "spring.main.banner-mode=off",
                            "spring.main.web-application-type=none")
                .run();
    }

}
