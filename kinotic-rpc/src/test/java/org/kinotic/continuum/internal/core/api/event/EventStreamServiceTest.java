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

import org.kinotic.continuum.core.api.event.EventStreamService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 *
 * Created by navid on 11/4/19
 */
//@ExtendWith(SpringExtension.class)
//@SpringBootTest
////@EmbeddedKafka TODO: embedded kafka
//@ActiveProfiles("test")
public class EventStreamServiceTest {

    private static final Logger log = LoggerFactory.getLogger(EventStreamServiceTest.class);

    private static final String DESTINATION_NO_KEY = "evt://org.kinotic.continuum.tests.TestEvent";
    private static final String DESTINATION_WITH_KEY = "evt://"+ UUID.randomUUID() +"@org.kinotic.continuum.tests.TestEvent";

    @Autowired
    private EventStreamService eventStreamService;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @Test
    public void testEventStreamWriteWithoutKey() throws Exception{
//        RawEvent event1 = new DefaultRawEvent(EventConstants.RAW_EVENT_FORMAT_UTF8, UUID.randomUUID().toString().getBytes());
//        log.debug("Writing Event "+event1);
//        Mono<Void> writeMono = eventStreamService.write(DESTINATION_NO_KEY, event1);
//
//        StepVerifier.create(writeMono).expectComplete().verify();
    }

    @Test
    public void testEventStreamWriteWithKey() throws Exception{
//        RawEvent event1 = new DefaultRawEvent(EventConstants.RAW_EVENT_FORMAT_UTF8, UUID.randomUUID().toString().getBytes());
//        log.debug("Writing Event "+event1);
//        Mono<Void> writeMono = eventStreamService.write(DESTINATION_WITH_KEY, event1);
//
//        StepVerifier.create(writeMono).expectComplete().verify();
    }

//    @Test(timeout = 60000)
//    public void testEventStreamTail() throws Exception{
//
//        // Write a value so stream will be available to tail.
//        RawEvent event1 = new DefaultRawEvent(EventConstants.RAW_EVENT_FORMAT_UTF8, UUID.randomUUID().toString().getBytes());
//        log.debug("Writing Event "+event1);
//        Mono<Void> writeMono = eventStreamService.write(DESTINATION, event1);
//
//        StepVerifier.create(writeMono).expectNextCount(1).expectComplete().verify();
//
//        RawEvent event2 = new DefaultRawEvent(EventConstants.RAW_EVENT_FORMAT_UTF8, UUID.randomUUID().toString().getBytes());
//        RawEvent event3 = new DefaultRawEvent(EventConstants.RAW_EVENT_FORMAT_UTF8, UUID.randomUUID().toString().getBytes());
//
//
//        Flux<RawEvent> tailFlux = eventStreamService.tail(DESTINATION)
//                                                    .doOnNext(rawEvent -> log.debug("Data received "+ new String(rawEvent.data())));
//
//        executorService.schedule(() -> {
//            log.debug("Writing Event "+event2);
//            eventStreamService.write(DESTINATION, event2).subscribe();
//        }, 2, TimeUnit.SECONDS);
//
//        executorService.schedule(() -> {
//            log.debug("Writing Event "+event3);
//            eventStreamService.write(DESTINATION, event3).subscribe();
//        }, 4, TimeUnit.SECONDS);
//
//        StepVerifier.create(tailFlux)
//                    .expectNextCount(4) // this will be last entry prior to us running this test, probably from last run
//                    //.expectNext(event1, event2, event3)
//                    .thenCancel().verify();
//    }

}
