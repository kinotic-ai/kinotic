package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.ListenerStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * Created by navid on 7/13/26
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class EventBusServiceTests {

    @Autowired
    private EventBusService eventBusService;

    /**
     * Pins the monitorListenerStatus contract: the initial status reflects existing listeners, removing
     * the last listener emits INACTIVE, and a listener re-registering emits ACTIVE. The monitor is fed
     * by the cluster manager's registration updates, so drift in that plumbing after a Vert.x upgrade
     * can only surface through these behavioral expectations, never as an error.
     * Together with RpcTests.testInfiniteFluxSurvivesUnrelatedConsumerChurn this covers both failure
     * directions: a monitor that goes deaf fails here, a monitor that matches foreign addresses fails there.
     */
    @Test
    public void testMonitorListenerStatusTracksListenerLifecycle() throws Exception {
        CRI cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, "org.kinotic.tests.MonitorLifecycleProbe");

        EventConsumer consumer = eventBusService.listen(cri);
        consumer.handler(event -> {});
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        AtomicReference<EventConsumer> relistened = new AtomicReference<>();
        try {
            StepVerifier.create(eventBusService.monitorListenerStatus(cri))
                        .expectNext(ListenerStatus.ACTIVE)
                        .then(consumer::unregister)
                        .expectNext(ListenerStatus.INACTIVE)
                        .then(() -> {
                            EventConsumer ec = eventBusService.listen(cri);
                            ec.handler(event -> {});
                            relistened.set(ec);
                        })
                        .expectNext(ListenerStatus.ACTIVE)
                        .thenCancel()
                        .verify(Duration.ofSeconds(15));
        } finally {
            EventConsumer ec = relistened.get();
            if(ec != null){
                ec.unregister();
            }
        }
    }
}
