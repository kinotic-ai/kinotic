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

package org.kinotic.continuum.internal.core.api.event;

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventStreamService;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 *
 * Created by navid on 10/23/19
 */
// FIXME: The built in service should use the Vertx event bus
@Component
public class DefaultEventStreamService implements EventStreamService {
    @Override
    public Mono<Void> send(Event<byte[]> event) {
        return null;
    }

    @Override
    public Mono<Void> sendStream(Publisher<Event<byte[]>> publisher) {
        return null;
    }

    @Override
    public Flux<Event<byte[]>> listen(CRI cri) {
        return null;
    }

//    @Autowired
//    private Vertx vertx;
//
//    @Autowired
//    private KafkaTemplate<String, byte[]> kafkaTemplate;
//
//    @Autowired
//    private KafkaSender<String, byte[]> kafkaSender;
//
//    @Autowired
//    private ContinuumProperties continuumProperties;
//
//    private Scheduler scheduler;
//
//    @PostConstruct
//    public void init(){
//        scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(v -> command.run(), null));
//    }
//
//    @Override
//    public Mono<Void> send(Event<byte[]> event) {
//        Validate.notNull(event, "Event must not be null");
//        Mono<Void> ret = Mono.create(sink -> {
//            String topic = event.cri().resourceName();
//            String key = event.cri().scope();
//
//            ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(topic,
//                                                                                 null,
//                                                                                 key,
//                                                                                 event.data(),
//                                                                                 new MetadataHeadersAdapter(event.metadata()));
//            kafkaTemplate.send(producerRecord)
//                         .addCallback(new ListenableFutureCallback<>() {
//                             @Override
//                             public void onFailure(Throwable ex) {
//                                 sink.error(ex);
//                             }
//
//                             @Override
//                             public void onSuccess(SendResult<String, byte[]> result) {
//                                 sink.success();
//                             }
//                         });
//        });
//
//        return ret.subscribeOn(scheduler);
//    }
//
//    public Mono<Void> sendStream(Publisher<Event<byte[]>> publisher){
//        Validate.notNull(publisher, "Publisher must not be null");
//        return kafkaSender.createOutbound()
//                          .send(Flux.from(publisher)
//                                    .map(event -> {
//                                        String topic = event.cri().resourceName();
//                                        String key = event.cri().scope();
//                                        return new ProducerRecord<>(topic,
//                                                                    null,
//                                                                    key,
//                                                                    event.data(),
//                                                                    new MetadataHeadersAdapter(event.metadata()));
//                                    }))
//                          .then();
//    }
//
//    @Override
//    public Flux<Event<byte[]>> listen(CRI cri) {
//        Map<String, Object> consumerProps = new HashMap<>();
//        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, continuumProperties.getKafkaBootstrapServers());
//        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
//        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
//
//        ReceiverOptions<String, byte[]> receiverOptions =
//                ReceiverOptions.<String, byte[]>create(consumerProps)
//                        .subscription(Collections.singleton(cri.resourceName()));
//
//        Flux<Event<byte[]>> ret = KafkaReceiver.create(receiverOptions)
//                            .receive()
//                            .map(receiverRecord -> {
//                                receiverRecord.receiverOffset().acknowledge();
//                                return new ReceiverRecordEventAdapter(cri, receiverRecord);
//                            });
//
//        return ret.subscribeOn(scheduler);
//    }

}
