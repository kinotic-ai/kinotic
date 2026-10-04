package org.kinotic.queue.internal;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.kinotic.queue.KinoticQueueLibrary;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.internal.cluster.ShardPlacement;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A queue node running in the test JVM: its own Ignite node, clustered Vert.x and the queue module's beans, as a
 * server wires them. Nodes find each other on ports apart from the ones a Kinotic server uses, so they never join
 * a cluster another test starts.
 */
public final class QueueTestNode implements AutoCloseable {

    private final String name;
    private final Path dataDirectory;
    private final Vertx vertx;
    private final AnnotationConfigApplicationContext context;
    private final NetworkFaults networkFaults;

    private QueueTestNode(String name,
                          Path dataDirectory,
                          Vertx vertx,
                          AnnotationConfigApplicationContext context,
                          NetworkFaults networkFaults) {
        this.name = name;
        this.dataDirectory = dataDirectory;
        this.vertx = vertx;
        this.context = context;
        this.networkFaults = networkFaults;
    }

    public static QueueTestNode start(String name, Path dataDirectory, int replicationFactor) throws Exception {
        IgniteConfiguration configuration = new IgniteConfiguration()
                .setIgniteInstanceName(name)
                .setGridLogger(new Slf4jLogger())
                .setMetricsLogFrequency(0)
                .setLocalHost("127.0.0.1")
                .setWorkDirectory(dataDirectory.resolveSibling(name + "-ignite").toString())
                .setUserAttributes(Map.of(ShardPlacement.QUEUE_NODE_ATTRIBUTE, Boolean.TRUE))
                // The event bus registry template KinoticIgniteConfigCaches gives every Kinotic node; a partitioned
                // registry without backups loses the registrations of live nodes when a node leaves
                .setCacheConfiguration(new CacheConfiguration<>("__vertx.*")
                                               .setCacheMode(CacheMode.REPLICATED)
                                               .setAtomicityMode(CacheAtomicityMode.ATOMIC)
                                               .setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC))
                .setDiscoverySpi(new TcpDiscoverySpi()
                                         .setLocalPort(48500)
                                         .setLocalPortRange(10)
                                         .setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(List.of("127.0.0.1:48500..48509"))))
                .setCommunicationSpi(new TcpCommunicationSpi().setLocalPort(48100).setLocalPortRange(10));
        Ignite ignite = Ignition.start(configuration);
        registerEventBusRegistryMetadata(ignite);
        Vertx vertx = Vertx.builder()
                           .with(new VertxOptions().setEventBusOptions(new EventBusOptions().setHost("127.0.0.1")))
                           .withClusterManager(new IgniteClusterManager(ignite))
                           .buildClustered()
                           .toCompletionStage().toCompletableFuture().get(2, TimeUnit.MINUTES);
        NetworkFaults networkFaults = new NetworkFaults();
        networkFaults.install(vertx);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
               .getPropertySources()
               .addFirst(new MapPropertySource("queue", Map.of("kinotic.queue.dataDirectory", dataDirectory.toString(),
                                                               "kinotic.queue.replicationFactor", replicationFactor)));
        context.registerBean(Ignite.class, () -> ignite);
        context.registerBean(Vertx.class, () -> vertx);
        context.register(KinoticQueueLibrary.class);
        context.refresh();
        return new QueueTestNode(name, dataDirectory, vertx, context, networkFaults);
    }

    /**
     * Several Ignite nodes in one JVM can send vertx-ignite's registry entry processors to each other before the
     * processors' binary schemas reach the cluster; the receiving node then blocks a system thread for
     * IGNITE_WAIT_SCHEMA_UPDATE (30 seconds). Serializing one of each when a node starts registers the schemas with
     * the cluster, and nodes that join later receive them with the cluster's metadata.
     */
    private static void registerEventBusRegistryMetadata(Ignite ignite) throws ReflectiveOperationException {
        IgniteRegistrationInfo info = new IgniteRegistrationInfo("kinotic.queue.test", new RegistrationInfo("node", 0, false));
        for (String processor : List.of("AddRegistrationProcessor", "RemoveRegistrationProcessor")) {
            Constructor<?> constructor = Class.forName("io.vertx.spi.cluster.ignite.impl.SubsMapHelper$" + processor)
                                              .getDeclaredConstructor(IgniteRegistrationInfo.class);
            constructor.setAccessible(true);
            ignite.binary().toBinary(constructor.newInstance(info));
        }
    }

    public QueueService queueService() {
        return context.getBean(QueueService.class);
    }

    public Vertx vertx() {
        return vertx;
    }

    public String name() {
        return name;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public NetworkFaults networkFaults() {
        return networkFaults;
    }

    /**
     * Stops the node gracefully: it stops owning its shards and leaves the cluster.
     */
    @Override
    public void close() throws Exception {
        context.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(1, TimeUnit.MINUTES);
        Ignition.stop(name, true);
    }

    /**
     * Stops the node as a dying process does: it goes silent at once, and the cluster sees it vanish without it
     * handing anything over. Files stay as they were on disk; the node's queue files are left open, as a process that
     * died leaves nothing to close them.
     */
    public void crash() throws Exception {
        networkFaults.isolate();
        Ignition.stop(name, true);
        vertx.close().otherwiseEmpty().toCompletionStage().toCompletableFuture().get(1, TimeUnit.MINUTES);
    }
}
