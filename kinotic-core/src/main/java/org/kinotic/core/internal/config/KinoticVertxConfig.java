package org.kinotic.core.internal.config;

import io.micrometer.core.instrument.Metrics;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.micrometer.MicrometerMetricsFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.internal.api.event.EventMessageCodec;
import org.kinotic.core.internal.KinoticIgniteClusterManager;
import org.kinotic.vertx.jackson3.VertxJackson3Codec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import static java.util.concurrent.TimeUnit.MINUTES;


/**
 * Created by navid on 4/16/15.
 */
@Configuration
public class KinoticVertxConfig {

    @Bean
    @ConditionalOnProperty(
            value="kinotic.disableClustering",
            havingValue = "false",
            matchIfMissing = true)
    public KinoticIgniteClusterManager clusterManager(Ignite ignite){
        if(ignite == null){
            throw new IllegalStateException("Something is wrong with the configuration Ignite is null");
        }
        // make sure clustering is enabled
        System.setProperty("vertx.clustered","true");

        return new KinoticIgniteClusterManager(ignite);
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
                       JsonMapper jsonMapper,
                       @Autowired(required = false) ClusterManager clusterManager) throws Throwable {

        // one mapper platform wide: vertx JSON binds with the Spring mapper, which carries every
        // JacksonModule bean the modules contribute (including vertxJackson3Module for the vertx types)
        VertxJackson3Codec.setMapper(jsonMapper);

        // The agent bridges Micrometer's global registry onto the OTLP exporter, so Vert.x's meters
        // need no exporter of their own. With no agent attached the registry has no backends.
        VertxOptions options = new VertxOptions()
                .setMetricsOptions(new MicrometerMetricsOptions().setEnabled(true));

        VertxBuilder builder = Vertx.builder()
                                    .withMetrics(new MicrometerMetricsFactory(Metrics.globalRegistry));
        Vertx vertx;

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

            vertx = builder.with(options.setEventBusOptions(eventBusOptions))
                           .withClusterManager(clusterManager)
                           .buildClustered()
                           .toCompletionStage()
                           .toCompletableFuture()
                           .get(2, MINUTES);
        }else{
            vertx = builder.with(options).build();
        }

        // Register the Event codec and auto-select it for any Event body, so callers never set a codec
        // name on delivery options. Local delivery still uses the codec's zero-copy transform().
        vertx.eventBus().registerCodec(new EventMessageCodec(jsonMapper));
        vertx.eventBus().codecSelector(body -> body instanceof Event ? EventMessageCodec.NAME : null);
        return vertx;
    }

}
