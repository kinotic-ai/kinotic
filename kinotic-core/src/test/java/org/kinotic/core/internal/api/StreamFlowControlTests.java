package org.kinotic.core.internal.api;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.internal.api.support.FlowControlTestService;
import org.kinotic.core.internal.api.support.StreamReplyProbe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Pins how a Java service's stream answers suspend and resume: a synchronous source pauses at a suspend, a
 * stream whose source ends while values are held keeps its ending until it is resumed, and a cancel drops a
 * stream whose ending is held.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class StreamFlowControlTests {

    @Autowired
    private ServiceRegistry serviceRegistry;
    @Autowired
    private EventBusService eventBusService;

    @Test
    public void testSuspendPausesASynchronousSource() throws Exception {
        int count = 200_000;
        ServiceIdentifier serviceIdentifier = register(new DefaultFlowControlTestService());
        try (StreamReplyProbe probe = new StreamReplyProbe(eventBusService, serviceIdentifier.cri(), "range")) {
            probe.request("[" + count + "]");
            probe.control(EventConstants.CONTROL_VALUE_SUSPEND);

            int receivedWhileSuspended = awaitNoMoreValues(probe);
            Assertions.assertTrue(receivedWhileSuspended < count,
                                  "the synchronous source sent all " + count + " values before the suspend was processed");
            Assertions.assertFalse(probe.completed());

            probe.control(EventConstants.CONTROL_VALUE_RESUME);
            Awaitility.await("the stream completes after the resume")
                      .atMost(Duration.ofSeconds(60))
                      .until(probe::completed);
            Assertions.assertEquals(IntStream.range(0, count).boxed().toList(), probe.values());
            Assertions.assertEquals(List.of(), probe.errors());
        } finally {
            unregister(serviceIdentifier);
        }
    }

    @Test
    public void testEndingWhileValuesAreHeldWaitsForTheResume() throws Exception {
        DefaultFlowControlTestService service = new DefaultFlowControlTestService();
        ServiceIdentifier serviceIdentifier = register(service);
        try (StreamReplyProbe probe = suspendedControlledStream(service, serviceIdentifier)) {
            // the source ends with values held behind the suspend
            for (int i = 1; i <= 20; i++) {
                service.sink.tryEmitNext(i);
            }
            service.sink.tryEmitComplete();

            Assertions.assertEquals(1, awaitNoMoreValues(probe), "a value was sent while the stream was suspended");
            Assertions.assertFalse(probe.completed(), "the completion was sent ahead of the values held behind it");

            probe.control(EventConstants.CONTROL_VALUE_RESUME);
            Awaitility.await("the stream completes after the resume")
                      .atMost(Duration.ofSeconds(10))
                      .until(probe::completed);
            Assertions.assertEquals(IntStream.rangeClosed(0, 20).boxed().toList(), probe.values());
            Assertions.assertEquals(List.of(), probe.errors());
        } finally {
            unregister(serviceIdentifier);
        }
    }

    @Test
    public void testCancelDropsAStreamWhoseEndingIsHeld() throws Exception {
        DefaultFlowControlTestService service = new DefaultFlowControlTestService();
        ServiceIdentifier serviceIdentifier = register(service);
        try (StreamReplyProbe probe = suspendedControlledStream(service, serviceIdentifier)) {
            for (int i = 1; i <= 20; i++) {
                service.sink.tryEmitNext(i);
            }
            service.sink.tryEmitComplete();
            awaitNoMoreValues(probe);

            probe.control(EventConstants.CONTROL_VALUE_CANCEL);
            // a resume after the cancel finds nothing held
            probe.control(EventConstants.CONTROL_VALUE_RESUME);
            Assertions.assertEquals(1, awaitNoMoreValues(probe));
            Assertions.assertFalse(probe.completed());
        } finally {
            unregister(serviceIdentifier);
        }
    }

    // Opens the controlled stream, sees its first value through, then suspends it
    private StreamReplyProbe suspendedControlledStream(DefaultFlowControlTestService service,
                                                       ServiceIdentifier serviceIdentifier) throws Exception {
        StreamReplyProbe probe = new StreamReplyProbe(eventBusService, serviceIdentifier.cri(), "controlled");
        probe.request("[]");
        Awaitility.await("the stream subscribed").atMost(Duration.ofSeconds(10)).until(() -> service.subscribed);
        service.sink.tryEmitNext(0);
        Awaitility.await("the first value arrives").atMost(Duration.ofSeconds(10)).until(() -> probe.valueCount() == 1);
        probe.control(EventConstants.CONTROL_VALUE_SUSPEND);
        // the suspend is processed on the service's context, after which the test feeds the source
        Thread.sleep(500);
        return probe;
    }

    // Waits until values stop arriving for a second and returns how many arrived. The reply consumer can share
    // the event loop a synchronous source runs on, so values are first awaited: no value yet can mean the loop is busy.
    private static int awaitNoMoreValues(StreamReplyProbe probe) {
        Awaitility.await("a value arrives")
                  .atMost(Duration.ofSeconds(30))
                  .until(() -> probe.valueCount() > 0);
        int[] last = {-1};
        Awaitility.await("values stop arriving")
                  .pollInterval(Duration.ofMillis(250))
                  .atMost(Duration.ofSeconds(30))
                  .until(() -> {
                      int now = probe.valueCount();
                      boolean settled = now == last[0];
                      last[0] = now;
                      return settled;
                  });
        Awaitility.await("no value arrives for a second")
                  .during(Duration.ofSeconds(1))
                  .atMost(Duration.ofSeconds(2))
                  .until(() -> probe.valueCount() == last[0]);
        return last[0];
    }

    private ServiceIdentifier register(FlowControlTestService service) throws Exception {
        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(null,
                                                                    "org.kinotic.tests",
                                                                    "FlowControlTestService-" + System.nanoTime(),
                                                                    null,
                                                                    "1.0.0");
        serviceRegistry.register(serviceIdentifier, FlowControlTestService.class, service)
                       .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        return serviceIdentifier;
    }

    private void unregister(ServiceIdentifier serviceIdentifier) throws Exception {
        serviceRegistry.unregister(serviceIdentifier).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static class DefaultFlowControlTestService implements FlowControlTestService {
        // a unicast sink emits only what its subscriber requested and keeps the rest
        final Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer();
        volatile boolean subscribed;

        @Override
        public Flux<Integer> range(int count) {
            return Flux.range(0, count);
        }

        @Override
        public Flux<Integer> controlled() {
            return sink.asFlux().doOnSubscribe(_ -> subscribed = true);
        }
    }

}
