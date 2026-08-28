package org.kinotic.core.internal;

import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.internal.api.event.DefaultEventBusService;
import org.kinotic.core.internal.api.event.EventMessageCodec;
import org.reactivestreams.Publisher;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link EventFabric} and {@link EventFabricBeanPostProcessor}: events emitted
 * through an {@link Emitter} flux travel the real event bus (codec included) and reach every
 * {@link Consumer} method. Uses a plain non-clustered Vert.x, publish still fans out locally.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
public class EventFabricTests {

    private Vertx vertx;
    private EventFabric eventFabric;
    private EventFabricBeanPostProcessor postProcessor;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        JsonMapper jsonMapper = JsonMapper.builder().build();
        // Same codec wiring KinoticVertxConfig applies, so events cross the bus exactly as in production
        vertx.eventBus().registerCodec(new EventMessageCodec(jsonMapper));
        vertx.eventBus().codecSelector(body -> body instanceof Event ? EventMessageCodec.NAME : null);
        eventFabric = new EventFabric(new DefaultEventBusService(null, vertx),
                                      jsonMapper,
                                      ReactiveAdapterRegistry.getSharedInstance());
        postProcessor = new EventFabricBeanPostProcessor(eventFabric);
    }

    @AfterEach
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void everyConsumerReceivesEveryEmittedEvent() throws Exception {
        TestProducer producer = wire(new TestProducer());
        TestReceiver first = wire(new TestReceiver(2));
        TestReceiver second = wire(new TestReceiver(2));

        producer.emit(new TestEvent("one", 1));
        producer.emit(new TestEvent("two", 2));

        assertTrue(first.latch.await(5, TimeUnit.SECONDS), "first consumer did not receive both events");
        assertTrue(second.latch.await(5, TimeUnit.SECONDS), "second consumer did not receive both events");
        assertEquals(List.of(new TestEvent("one", 1), new TestEvent("two", 2)), first.received);
        assertEquals(first.received, second.received);
    }

    @Test
    public void consumerThrowingDoesNotAffectOtherConsumers() throws Exception {
        TestProducer producer = wire(new TestProducer());
        wire(new ThrowingReceiver());
        TestReceiver receiver = wire(new TestReceiver(1));

        producer.emit(new TestEvent("survives", 42));

        assertTrue(receiver.latch.await(5, TimeUnit.SECONDS), "consumer was affected by another consumer throwing");
        assertEquals(new TestEvent("survives", 42), receiver.received.getFirst());
    }

    @Test
    public void unwiredBeansStopEmittingAndReceiving() throws Exception {
        TestProducer producer = wire(new TestProducer());
        TestReceiver receiver = wire(new TestReceiver(1));

        producer.emit(new TestEvent("first", 1));
        assertTrue(receiver.latch.await(5, TimeUnit.SECONDS));

        assertTrue(eventFabric.isWired(receiver));
        postProcessor.postProcessBeforeDestruction(receiver, "receiver");
        assertFalse(eventFabric.isWired(receiver));

        producer.emit(new TestEvent("second", 2));
        // Delivery is asynchronous, so allow time for a wrongly delivered event to arrive
        Thread.sleep(250);
        assertEquals(1, receiver.received.size(), "unwired consumer still received an event");

        postProcessor.postProcessBeforeDestruction(producer, "producer");
        assertEquals(0, producer.sink.currentSubscriberCount(), "emitter subscription was not disposed");
    }

    @Test
    public void emitterMayReturnAnyMultiValueReactiveType() throws Exception {
        PublisherProducer producer = wire(new PublisherProducer());
        TestReceiver receiver = wire(new TestReceiver(1));

        producer.emit(new TestEvent("publisher", 7));

        assertTrue(receiver.latch.await(5, TimeUnit.SECONDS), "event from a Publisher emitter was not received");
        assertEquals(new TestEvent("publisher", 7), receiver.received.getFirst());
    }

    @Test
    public void invalidEmitterFailsBeanInitialization() {
        assertThrows(FatalBeanException.class,
                     () -> postProcessor.postProcessAfterInitialization(new InvalidEmitter(), "invalidEmitter"));
    }

    @Test
    public void singleValueEmitterFailsBeanInitialization() {
        assertThrows(FatalBeanException.class,
                     () -> postProcessor.postProcessAfterInitialization(new SingleValueEmitter(), "singleValueEmitter"));
    }

    @Test
    public void invalidConsumerFailsBeanInitialization() {
        assertThrows(FatalBeanException.class,
                     () -> postProcessor.postProcessAfterInitialization(new InvalidConsumer(), "invalidConsumer"));
    }

    private <T> T wire(T bean) {
        postProcessor.postProcessAfterInitialization(bean, bean.getClass().getSimpleName());
        return bean;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestEvent {
        private String name;
        private int number;
    }

    public static class TestProducer {
        private final Sinks.Many<TestEvent> sink = Sinks.many().multicast().directBestEffort();

        @Emitter
        Flux<TestEvent> events() {
            return sink.asFlux();
        }

        void emit(TestEvent event) {
            sink.tryEmitNext(event);
        }
    }

    public static class TestReceiver {
        private final List<TestEvent> received = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        TestReceiver(int expectedEvents) {
            latch = new CountDownLatch(expectedEvents);
        }

        @Consumer
        void onEvent(TestEvent event) {
            received.add(event);
            latch.countDown();
        }
    }

    public static class ThrowingReceiver {
        @Consumer
        void onEvent(TestEvent event) {
            throw new IllegalStateException("This consumer always fails");
        }
    }

    public static class PublisherProducer {
        private final Sinks.Many<TestEvent> sink = Sinks.many().multicast().directBestEffort();

        @Emitter
        Publisher<TestEvent> events() {
            return sink.asFlux();
        }

        void emit(TestEvent event) {
            sink.tryEmitNext(event);
        }
    }

    public static class InvalidEmitter {
        @Emitter
        Flux<TestEvent> events(String unexpectedParameter) {
            return Flux.empty();
        }
    }

    public static class SingleValueEmitter {
        @Emitter
        Mono<TestEvent> event() {
            return Mono.empty();
        }
    }

    public static class InvalidConsumer {
        @Consumer
        String onEvent(TestEvent event) {
            return "consumers must return void";
        }
    }
}
