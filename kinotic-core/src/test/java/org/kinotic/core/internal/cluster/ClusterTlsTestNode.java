package org.kinotic.core.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.internal.processors.odbc.ClientListenerProcessor;
import org.kinotic.core.internal.TestApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A Kinotic node run in its own process by {@link ClusterTlsTests}. Once started it prints the port its thin client
 * connector took, as a line {@code clientPort=<port>}, then the number of server nodes in its cluster every half
 * second, as a line {@code servers=<count>}.
 */
public final class ClusterTlsTestNode {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(TestApplication.class, args);
        Ignite ignite = context.getBean(Ignite.class);
        System.out.println("clientPort=" + ignite.cluster().localNode().attribute(ClientListenerProcessor.CLIENT_LISTENER_PORT));
        while (true) {
            System.out.println("servers=" + ignite.cluster().forServers().nodes().size());
            System.out.flush();
            Thread.sleep(500);
        }
    }
}
