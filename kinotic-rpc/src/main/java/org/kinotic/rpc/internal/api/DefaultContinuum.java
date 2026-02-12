/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.rpc.internal.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.text.WordUtils;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.awaitility.Awaitility;
import org.kinotic.rpc.api.Continuum;
import org.kinotic.rpc.api.ServerInfo;
import org.kinotic.rpc.api.annotations.ContinuumPackages;
import org.kinotic.rpc.api.annotations.EnableContinuum;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.rpc.api.config.IgniteClusterProperties;
import org.kinotic.rpc.internal.utils.ContinuumUtil;
import org.kinotic.rpc.internal.utils.MetaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;


/**
 * Provides information about the Continuum process and handles controlled shutdown of Vertx and Ignite.
 * Created by navid on 9/24/19
 */
@Component
public class DefaultContinuum implements Continuum {

    private static final int ADJECTIVE_COUNT = 1915;
    private static final int ANIMAL_COUNT = 587;
    private static final Logger log = LoggerFactory.getLogger(DefaultContinuum.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss z");
    private final ContinuumProperties continuumProperties;
    private final IgniteClusterProperties igniteClusterProperties;
    private final ServerInfo serverInfo;
    private final Vertx vertx;
    private String applicationName;
    private String applicationVersion;

    public DefaultContinuum(ResourceLoader resourceLoader,
                            @Autowired(required = false)
                            ClusterManager clusterManager,
                            Vertx vertx,
                            ApplicationContext applicationContext,
                            ContinuumProperties continuumProperties,
                            IgniteClusterProperties igniteClusterProperties,
                            ReactiveAdapterRegistry reactiveAdapterRegistry) throws IOException {
        String nodeName;

        try (InputStreamReader reader = new InputStreamReader(resourceLoader.getResource("classpath:adjectives.txt").getInputStream());
                Stream<String> fileStream = new BufferedReader(reader).lines()) {
                nodeName = fileStream.skip(ContinuumUtil.getRandomNumberInRange(ADJECTIVE_COUNT))
                                 .findFirst()
                                 .orElse("");
        }

        try (InputStreamReader reader = new InputStreamReader(resourceLoader.getResource("classpath:animals.txt").getInputStream());
             Stream<String> fileStream = new BufferedReader(reader).lines()) {
             String temp = fileStream.skip(ContinuumUtil.getRandomNumberInRange(ANIMAL_COUNT))
                                    .findFirst()
                                    .orElse("");
             nodeName = nodeName + " " + WordUtils.capitalize(temp);
        }
        this.vertx = vertx;
        this.continuumProperties = continuumProperties;
        this.igniteClusterProperties = igniteClusterProperties;
        String nodeId = (clusterManager != null  ?  clusterManager.getNodeId() : UUID.randomUUID().toString());
        this.serverInfo = new ServerInfo(nodeId, nodeName);

        // find Continuum application name
        List<String> packages = ContinuumPackages.get(applicationContext);
        MetadataReader[] readers = MetaUtil.findClassesWithAnnotation(applicationContext, packages, EnableContinuum.class)
                                           .toArray(new MetadataReader[0]);

        MetadataReader readerToUse = null;
        for(MetadataReader reader : readers) {
            if(reader.getAnnotationMetadata().hasAnnotation(SpringBootApplication.class.getName())){
                readerToUse = reader;
                break;
            }
        }

        if(readerToUse != null){
            Map<String, Object> annotationAttributes = readerToUse.getAnnotationMetadata()
                                                                  .getAnnotationAttributes(EnableContinuum.class.getName());
            if(annotationAttributes != null){
                applicationName = (String) annotationAttributes.get("name");
                applicationVersion = (String) annotationAttributes.get("version");
            }
            if(applicationName == null){
                applicationName = ClassUtils.getShortCanonicalName(readerToUse.getClassMetadata().getClassName());
            }
        }else{
            // Probably will not happen!
            log.warn("No @SpringBootApplication could be found with @EnableContinuum annotation.");
        }

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

    @Override
    public String applicationName() {
        return applicationName;
    }

    @Override
    public String applicationVersion() {
        return applicationVersion;
    }

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        StringBuilder info = new StringBuilder("\n\n##### Continuum Process Started #####\n\n\t");
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
        info.append(continuumProperties.toString());
        info.append("\n\nIgnite Cluster Properties:");
        info.append(igniteClusterProperties.toString());

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
    public void shutdown() {
        Future<?> future = vertx.close();
        Awaitility.await().atMost(2, TimeUnit.MINUTES).until(future::isComplete);
    }
}
