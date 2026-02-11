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

package org.kinotic.continuum.gateway.internal.hft;

import org.kinotic.continuum.core.api.event.Event;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Allows writing to multiple  ChronicleQueue transparently
 *
 *
 * Created by Navid Mitchell on 11/4/20
 */
@Component
public class DefaultHFTQueueManager implements HFTQueueManager {
    @Override
    public Mono<Void> write(Event<byte[]> event) {
        return null;
    }

//    private static final Logger log = LoggerFactory.getLogger(DefaultHFTQueueManager.class);
//
//    private final LoadingCache<String, ChronicleQueue> cache;
//
//    public DefaultHFTQueueManager(final ContinuumGatewayProperties gatewayProperties) {
//
//        cache = Caffeine.newBuilder()
//                        .expireAfterAccess(1, TimeUnit.HOURS)
//                        .removalListener((String key, ChronicleQueue queue, RemovalCause cause) -> {
//                            if(queue != null){
//                                queue.close();
//                            }
//                        })
//                        .build(key -> {
//                            try {
//                                Path queuePath = Path.of(gatewayProperties.getDataDir(), key);
//                                Files.createDirectories(queuePath.getParent());
//                                return SingleChronicleQueueBuilder.binary(queuePath)
//                                                                  .rollCycle(RollCycles.FAST_HOURLY)
//                                                                  .storeFileListener((cycle, file) -> log.debug("HFT releasing file "+file))
//                                                                  .build();
//                            } catch (Exception e) {
//                                log.error("Could not build HFT Queue for "+key, e);
//                                throw e;
//                            }
//                        });
//    }
//
//    @Override
//    public Mono<Void> write(Event<byte[]> event){
//        return Mono.create(sink -> {
//
//            String key = event.cri().resourceName().replace(".", "_");
//
//            ChronicleQueue queue = cache.get(key);
//            if(queue != null){
//                HftRawEvent hftRawEvent = GatewayUtils.continuumEventToHftRawEvent(event);
//
//                ExcerptAppender hftQueueAppender = queue.acquireAppender();
//                boolean success = false;
//                // store all events in a hft queue for redundancy
//                try (final DocumentContext dc = hftQueueAppender.writingDocument()) {
//
//                    GatewayUtils.writeHftRawEvent(hftRawEvent, dc);
//
//                    success = true;
//                }catch(Exception e){
//                    sink.error(new IllegalStateException("Error writing to HFT Queue", e));
//                }
//
//                // Done outside of try with resource to not block DocumentContext any longer than needed
//                if(success){
//                    sink.success();
//                }
//
//            }else{
//                sink.error(new IllegalStateException("No HFT Queue is available for "+key));
//            }
//
//        });
//    }


}
