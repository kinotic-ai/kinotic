package org.kinotic.client.gateway;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.SecurityService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A gateway for the client tests: kinotic-core and the api gateway in one Ignite node of their own, with
 * {@link TestGatewaySecurityService} authenticating connections, {@link ClientTestService} to call and
 * {@link RestrictedService} in a zone the test participant may not address.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@EnableKinotic
public class TestGatewayApplication {

    public static final String ZONE = "client-test";
    public static final String RESTRICTED_ZONE = "restricted";
    public static final String CLIENT_SECRET = "test-secret";

    @Bean
    public SecurityService securityService() {
        return new TestGatewaySecurityService();
    }

    @Bean
    public ZoneRulesResolver zoneRulesResolver() {
        return participant -> ZoneRules.of(Set.of(ZONE), Set.of());
    }

    /**
     * Starts the gateway on the port and returns once its STOMP endpoint accepts connections. Ignite discovers on
     * free ports and works in a directory of this gateway's own, so a test JVM running alongside, this suite's
     * included, never joins this node.
     */
    public static ConfigurableApplicationContext start(int port) throws Exception {
        ConfigurableApplicationContext ret = new SpringApplicationBuilder(TestGatewayApplication.class)
                .properties("kinotic.apiGateway.stompPort=" + port,
                            "kinotic.ignite.discoveryType=LOCAL",
                            "kinotic.ignite.discoveryPort=" + freePort(),
                            "kinotic.ignite.communicationPort=" + freePort(),
                            "kinotic.ignite.workDirectory=" + Files.createTempDirectory(Files.createDirectories(Path.of("build", "ignite").toAbsolutePath()), "node-"),
                            "spring.main.banner-mode=off")
                .run();
        // the gateway verticles bind the port after the context is up
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!accepting(port)) {
            if (System.nanoTime() > deadline) {
                ret.close();
                throw new IllegalStateException("The gateway did not bind port " + port + " in time");
            }
            Thread.sleep(100);
        }
        return ret;
    }

    /**
     * @return a port nothing listens on at the moment
     */
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean accepting(int port) {
        boolean ret;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            ret = true;
        } catch (IOException e) {
            ret = false;
        }
        return ret;
    }

}
