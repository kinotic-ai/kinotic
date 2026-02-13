

package org.kinotic.boot.internal;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import jakarta.annotation.PreDestroy;
import org.apache.commons.text.WordUtils;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.kinotic.boot.api.Kinotic;
import org.kinotic.boot.api.ServerInfo;
import org.kinotic.boot.api.config.IgniteProperties;
import org.kinotic.boot.api.config.KinoticProperties;
import org.kinotic.boot.internal.utils.KinoticUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;


/**
 * Provides information about the Continuum process and handles controlled shutdown of Vertx and Ignite.
 * Created by navid on 9/24/19
 */
@Component
public class DefaultKinotic implements Kinotic {

    private static final int ADJECTIVE_COUNT = 1915;
    private static final int ANIMAL_COUNT = 587;
    private static final Logger log = LoggerFactory.getLogger(DefaultKinotic.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss z");
    private final KinoticProperties kinoticProperties;
    private final IgniteProperties igniteProperties;
    private final ServerInfo serverInfo;
    private final Vertx vertx;

    public DefaultKinotic(ResourceLoader resourceLoader,
                          @Autowired(required = false)
                            ClusterManager clusterManager,
                          Vertx vertx,
                          KinoticProperties kinoticProperties,
                          IgniteProperties igniteProperties,
                          ReactiveAdapterRegistry reactiveAdapterRegistry) throws IOException {
        String nodeName;

        try (InputStreamReader reader = new InputStreamReader(resourceLoader.getResource("classpath:adjectives.txt").getInputStream());
                Stream<String> fileStream = new BufferedReader(reader).lines()) {
                nodeName = fileStream.skip(KinoticUtil.getRandomNumberInRange(ADJECTIVE_COUNT))
                                 .findFirst()
                                 .orElse("");
        }

        try (InputStreamReader reader = new InputStreamReader(resourceLoader.getResource("classpath:animals.txt").getInputStream());
             Stream<String> fileStream = new BufferedReader(reader).lines()) {
             String temp = fileStream.skip(KinoticUtil.getRandomNumberInRange(ANIMAL_COUNT))
                                    .findFirst()
                                    .orElse("");
             nodeName = nodeName + " " + WordUtils.capitalize(temp);
        }
        this.vertx = vertx;
        this.kinoticProperties = kinoticProperties;
        this.igniteProperties = igniteProperties;
        String nodeId = (clusterManager != null  ?  clusterManager.getNodeId() : UUID.randomUUID().toString());
        this.serverInfo = new ServerInfo(nodeId, nodeName);

        // Register Vertx Future with Reactor
        reactiveAdapterRegistry.registerReactiveType(ReactiveTypeDescriptor.singleOptionalValue(Future.class,
                                                                                                (Supplier<Future<?>>) Future::succeededFuture),
                                                     source -> {
                                                         Future<?> future = (Future<?>) source;
                                                         return Mono.create(monoSink -> {
                                                             future.onComplete(event -> {
                                                                 if (event.succeeded()) {
                                                                     monoSink.success(event.result());
                                                                 } else {
                                                                     monoSink.error(event.cause());
                                                                 }
                                                             });
                                                         });
                                                     },
                                                     publisher -> Future.future(promise -> Mono.from(publisher)
                                                                                               .doOnSuccess((Consumer<Object>) o -> {
                                                                                                   if(o != null){
                                                                                                       promise.complete(o);
                                                                                                   }else{
                                                                                                       promise.complete();
                                                                                                   }
                                                                                               })
                                                                                               .subscribe(v -> {}, promise::fail)));// We use an empty consumer this is handled with doOnSuccess, this is done so we get a single "signal" instead of onNext, onComplete type logic..


    }

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        StringBuilder info = new StringBuilder("\n\n##### Kinotic Process Started #####\n\n\t");
        info.append(serverInfo.getNodeName());
        info.append("\n\tNode Id: ");
        info.append(serverInfo.getNodeId());
        info.append("\n\t");
        info.append(sdf.format(new Date()));
        info.append("\n\n\tHost IPs:");
        for(String ip : U.allLocalIps()){
            info.append("\n\t\t");
            info.append(ip);
        }
        info.append("\n\n");
        info.append(kinoticProperties.toString());
        info.append("\n\nIgnite Cluster Properties:");
        info.append(igniteProperties.toString());

        log.info(info.toString());
    }

    @Override
    public ServerInfo serverInfo() {
        return serverInfo;
    }

    /**
     * Properly shutdown vertx instance on application shutdown
     * Waiting up to 2 minutes for shutdown to complete
     */
    @PreDestroy
    public void shutdown() throws TimeoutException {
        Future<?> future = vertx.close();
        future.await(2, TimeUnit.MINUTES);
    }

}
