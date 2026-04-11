package org.kinotic.core.internal.config;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.security.Participant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.concurrent.TimeUnit.MINUTES;


/**
 * Created by navid on 4/16/15.
 */
@Configuration
public class KinoticVertxConfig {

    /**
     * Provides access to the {@link Participant} associated with the current Vert.x context.
     * The {@link Participant} is set by the service invocation infrastructure before a service method is called,
     * and is available for the duration of the invocation including nested method calls and {@code vertx.executeBlocking()} blocks.
     * <p>
     * Defined as a static inner class of {@link KinoticVertxConfig} to ensure the {@link ContextLocal} is registered
     * before the {@link Vertx} instance is created.
     */
    public static class ParticipantContext {

        private static final ContextLocal<Participant> CONTEXT_LOCAL = ContextLocal.registerLocal(Participant.class);

        /**
         * Returns the {@link Participant} for the current Vert.x context, or null if none is set.
         *
         * @return the current {@link Participant} or null
         */
        public static Participant currentParticipant() {
            Context context = Vertx.currentContext();
            if (context != null) {
                return context.getLocal(CONTEXT_LOCAL);
            }
            return null;
        }

        /**
         * Sets the {@link Participant} on the given Vert.x context.
         *
         * @param context the Vert.x {@link Context}
         * @param participant the {@link Participant} to set
         */
        public static void setParticipant(Context context, Participant participant) {
            context.putLocal(CONTEXT_LOCAL, participant);
        }

    }

    @Bean
    @ConditionalOnProperty(
            value="kinotic.disableClustering",
            havingValue = "false",
            matchIfMissing = true)
    public ClusterManager clusterManager(Ignite ignite){
        if(ignite == null){
            throw new IllegalStateException("Something is wrong with the configuration Ignite is null");
        }
        // make sure clustering is enabled
        System.setProperty("vertx.clustered","true");

        return new IgniteClusterManager(ignite);
    }

    @Bean
    public EventBus eventBus(Vertx vertx) {
        return vertx.eventBus();
    }

    @Bean
    public FileSystem fileSystem(Vertx vertx) {
        return vertx.fileSystem();
    }

    @Bean
    public SharedData sharedData(Vertx vertx) {
        return vertx.sharedData();
    }

    @Bean
    public Vertx vertx(KinoticProperties properties,
                       @Autowired(required = false) ClusterManager clusterManager) throws Throwable {

        VertxBuilder builder = Vertx.builder();

        if (clusterManager != null) {

            EventBusOptions eventBusOptions = new EventBusOptions();
            eventBusOptions.setPort(properties.getEventBusClusterPort());
            eventBusOptions.setHost(properties.getEventBusClusterHost());

            if(properties.getEventBusClusterPublicPort() != -1) {
                eventBusOptions.setClusterPublicPort(properties.getEventBusClusterPublicPort());
            }
            if(properties.getEventBusClusterPublicHost() != null) {
                eventBusOptions.setClusterPublicHost(properties.getEventBusClusterPublicHost());
            }

            VertxOptions options = new VertxOptions()
                    .setEventBusOptions(eventBusOptions);

            return builder.with(options)
                          .withClusterManager(clusterManager)
                          .buildClustered()
                          .toCompletionStage()
                          .toCompletableFuture()
                          .get(2, MINUTES);
        }else{
            return builder.build();
        }
    }

}
