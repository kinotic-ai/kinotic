package org.kinotic.core.internal.config;

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
import org.kinotic.core.api.security.ParticipantContext;
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
     * {@link ContextLocal} for storing the {@link Participant} on the Vert.x context.
     * Must be registered before any {@link Vertx} instance is created.
     */
    private static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);

    @Bean
    public ParticipantContext participantContext() {
        return new ParticipantContext(PARTICIPANT_LOCAL);
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
