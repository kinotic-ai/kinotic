package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.KinoticConsumer;
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.internal.api.event.ConsumerMethodRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins that a confirmed event reaches the nodes registered as consumers of its type through the registrations
 * Vert.x keeps in Ignite and the compute job the publisher broadcasts, and that the publish completes only once
 * they have handled it.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class ConfirmedEventTests {

    @Autowired
    private EventService eventService;

    @Autowired
    private ConsumerMethodRegistry consumerMethodRegistry;

    @Test
    public void aConfirmedEventIsHandledBeforeThePublishCompletes() throws Exception {
        ProbeRecorder recorder = new ProbeRecorder();
        await(consumerMethodRegistry.register(recorder, ProbeRecorder.class.getDeclaredMethod("onProbe", Probe.class)));
        try {
            Probe probe = new Probe("probe-" + UUID.randomUUID());

            await(eventService.publishConfirmed(probe));

            assertEquals(List.of(probe), recorder.received);
        } finally {
            consumerMethodRegistry.unregister(recorder);
        }
    }

    @Test
    public void aConfirmedEventFailsWhenItsConsumerKeepsFailing() throws Exception {
        RefusingConsumer refusing = new RefusingConsumer();
        await(consumerMethodRegistry.register(refusing, RefusingConsumer.class.getDeclaredMethod("onRefused", Refused.class)));
        try {
            assertThrows(ExecutionException.class, () -> await(eventService.publishConfirmed(new Refused("refused"))));
        } finally {
            consumerMethodRegistry.unregister(refusing);
        }
    }

    @Test
    public void aConfirmedEventNobodyConsumesCompletes() throws Exception {
        await(eventService.publishConfirmed(new Unconsumed("nobody")));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Probe {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Refused {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Unconsumed {
        private String name;
    }

    public static class ProbeRecorder {
        private final List<Probe> received = new CopyOnWriteArrayList<>();

        @KinoticConsumer
        void onProbe(Probe probe) {
            received.add(probe);
        }
    }

    public static class RefusingConsumer {
        @KinoticConsumer
        Future<Void> onRefused(Refused refused) {
            return Future.failedFuture(new IllegalStateException("This consumer always fails"));
        }
    }

}
