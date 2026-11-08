package org.kinotic.core.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.RegistrationInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ignite.Ignite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.KinoticConsumer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventDelivery;
import org.kinotic.core.internal.api.event.ConfirmedEventDispatchJob;
import org.kinotic.core.internal.api.event.ConsumerMethodRegistry;
import org.kinotic.core.internal.api.event.DefaultEventBusService;
import org.kinotic.core.internal.api.event.DefaultEventService;
import org.kinotic.core.internal.api.event.DefaultEventStreamService;
import org.kinotic.core.internal.api.event.EventMessageCodec;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link DefaultEventService#publish(Object)}, the {@link ConsumerMethodRegistry} and the
 * {@link ConfirmedEventDispatchJob}: published events travel the real event bus (codec included) and reach every
 * best-effort {@link KinoticConsumer} method, and a confirmed delivery to a node returns once its confirmed consumers
 * have handled the event. Uses a plain non-clustered Vert.x, publish still fans out locally.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
public class EventServiceTests {

    private Vertx vertx;
    private JsonMapper jsonMapper;
    private ConsumerMethodRegistry registry;
    private ConsumerBeanPostProcessor postProcessor;
    private DefaultEventService eventService;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        jsonMapper = JsonMapper.builder().build();
        // Same codec wiring KinoticVertxConfig applies, so events cross the bus exactly as in production
        vertx.eventBus().registerCodec(new EventMessageCodec(jsonMapper));
        vertx.eventBus().codecSelector(body -> body instanceof Event ? EventMessageCodec.NAME : null);
        // A plain Vert.x has no node id; a consumer's acknowledgement names the one the cluster manager reports
        KinoticIgniteClusterManager clusterManager = mock(KinoticIgniteClusterManager.class);
        when(clusterManager.getNodeId()).thenReturn("test-node");
        // no node holds a confirmed registration, so publish dispatches to none
        doAnswer(invocation -> {
            invocation.<Completable<List<RegistrationInfo>>>getArgument(1).succeed(List.of());
            return null;
        }).when(clusterManager).getClusterRegistrations(anyString(), any());
        DefaultEventBusService eventBusService = new DefaultEventBusService(clusterManager, vertx);
        registry = new ConsumerMethodRegistry(eventBusService, jsonMapper, vertx);
        postProcessor = new ConsumerBeanPostProcessor(new ObjectProvider<>() {
            @Override
            public ConsumerMethodRegistry getObject() {
                return registry;
            }
        });
        // publish never reaches Ignite, which deliveries to confirmed consumers are broadcast over
        eventService = new DefaultEventService(eventBusService, new DefaultEventStreamService(), jsonMapper,
                                               mock(Ignite.class), clusterManager, vertx, OpenTelemetry.noop());
    }

    @AfterEach
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void everyConsumerReceivesEveryPublishedEvent() throws Exception {
        TestReceiver first = register(new TestReceiver(2));
        TestReceiver second = register(new TestReceiver(2));

        eventService.publish(new TestEvent("one", 1));
        eventService.publish(new TestEvent("two", 2));

        assertTrue(first.latch.await(5, TimeUnit.SECONDS), "first consumer did not receive both events");
        assertTrue(second.latch.await(5, TimeUnit.SECONDS), "second consumer did not receive both events");
        assertEquals(List.of(new TestEvent("one", 1), new TestEvent("two", 2)), first.received);
        assertEquals(first.received, second.received);
    }

    @Test
    public void consumerFailingDoesNotAffectOtherConsumers() throws Exception {
        register(new ThrowingReceiver());
        register(new FailingReceiver());
        TestReceiver receiver = register(new TestReceiver(1));

        eventService.publish(new TestEvent("survives", 42));

        assertTrue(receiver.latch.await(5, TimeUnit.SECONDS), "consumer was affected by another consumer failing");
        assertEquals(new TestEvent("survives", 42), receiver.received.getFirst());
    }

    @Test
    public void unregisteredBeansStopReceiving() throws Exception {
        TestReceiver receiver = register(new TestReceiver(1));

        eventService.publish(new TestEvent("first", 1));
        assertTrue(receiver.latch.await(5, TimeUnit.SECONDS));

        assertTrue(registry.isRegistered(receiver));
        postProcessor.postProcessBeforeDestruction(receiver, "receiver");
        assertFalse(registry.isRegistered(receiver));

        eventService.publish(new TestEvent("second", 2));
        // Delivery is asynchronous, so allow time for a wrongly delivered event to arrive
        Thread.sleep(250);
        assertEquals(1, receiver.received.size(), "unregistered consumer still received an event");
    }

    @Test
    public void publishSendsOnlyToBestEffortConsumersOverTheBus() throws Exception {
        TestReceiver bestEffort = register(new TestReceiver(1));
        ConfirmedReceiver confirmed = register(new ConfirmedReceiver());

        await(eventService.publish(new TestEvent("best-effort", 6)));

        assertTrue(bestEffort.latch.await(5, TimeUnit.SECONDS), "best-effort consumer did not receive the event");
        // Delivery is asynchronous, so allow time for a wrongly delivered event to arrive
        Thread.sleep(250);
        assertEquals(List.of(), confirmed.received, "confirmed consumer received the event over the bus");
    }

    @Test
    public void confirmedDeliveryReturnsOnceEveryConfirmedConsumerHasHandledTheEvent() {
        ConfirmedReceiver receiver = register(new ConfirmedReceiver());
        SlowReceiver slow = register(new SlowReceiver(vertx));

        dispatch(new TestEvent("confirmed", 3));

        assertEquals(List.of(new TestEvent("confirmed", 3)), receiver.received);
        assertEquals(List.of(new TestEvent("confirmed", 3)), slow.handled);
    }

    @Test
    public void confirmedDeliverySkipsBestEffortConsumers() {
        TestReceiver bestEffort = register(new TestReceiver(1));
        ConfirmedReceiver confirmed = register(new ConfirmedReceiver());

        dispatch(new TestEvent("confirmed only", 7));

        assertEquals(List.of(new TestEvent("confirmed only", 7)), confirmed.received);
        assertEquals(List.of(), bestEffort.received);
    }

    @Test
    public void confirmedDeliveryFailsWhenAConsumerFails() {
        register(new ConfirmedReceiver());
        register(new ConfirmedFailingReceiver());

        assertThrows(IllegalStateException.class, () -> dispatch(new TestEvent("refused", 4)));
    }

    @Test
    public void confirmedDeliveryToANodeWithoutConsumersSucceeds() {
        dispatch(new TestEvent("nobody", 5));
    }

    @Test
    public void invalidConsumerFailsBeanInitialization() {
        assertThrows(FatalBeanException.class,
                     () -> postProcessor.postProcessAfterInitialization(new InvalidConsumer(), "invalidConsumer"));
    }

    @Test
    public void consumerReturningAValueFailsBeanInitialization() {
        assertThrows(FatalBeanException.class,
                     () -> postProcessor.postProcessAfterInitialization(new ValueReturningConsumer(), "valueReturningConsumer"));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private <T> T register(T bean) {
        postProcessor.postProcessAfterInitialization(bean, bean.getClass().getSimpleName());
        return bean;
    }

    // runs the job the publisher broadcasts as a node receiving it does
    private void dispatch(TestEvent event) {
        ConfirmedEventDispatchJob job = new ConfirmedEventDispatchJob(TestEvent.class.getName(), jsonMapper.writeValueAsBytes(event));
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(ConsumerMethodRegistry.class, () -> registry);
            context.refresh();
            // Ignite injects the receiving node's context into the field as the job is deployed there
            ReflectionTestUtils.setField(job, "applicationContext", context);
            job.run();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestEvent {
        private String name;
        private int number;
    }

    public static class TestReceiver {
        private final List<TestEvent> received = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        TestReceiver(int expectedEvents) {
            latch = new CountDownLatch(expectedEvents);
        }

        @KinoticConsumer
        void onEvent(TestEvent event) {
            received.add(event);
            latch.countDown();
        }
    }

    public static class ConfirmedReceiver {
        private final List<TestEvent> received = new CopyOnWriteArrayList<>();

        @KinoticConsumer(delivery = EventDelivery.CONFIRMED)
        void onEvent(TestEvent event) {
            received.add(event);
        }
    }

    public static class SlowReceiver {
        private final List<TestEvent> handled = new CopyOnWriteArrayList<>();
        private final Vertx vertx;

        SlowReceiver(Vertx vertx) {
            this.vertx = vertx;
        }

        @KinoticConsumer(delivery = EventDelivery.CONFIRMED)
        Future<Void> onEvent(TestEvent event) {
            return vertx.timer(200).map(id -> {
                handled.add(event);
                return null;
            });
        }
    }

    public static class ThrowingReceiver {
        @KinoticConsumer
        void onEvent(TestEvent event) {
            throw new IllegalStateException("This consumer always fails");
        }
    }

    public static class FailingReceiver {
        @KinoticConsumer
        Future<Void> onEvent(TestEvent event) {
            return Future.failedFuture(new IllegalStateException("This consumer always fails"));
        }
    }

    public static class ConfirmedFailingReceiver {
        @KinoticConsumer(delivery = EventDelivery.CONFIRMED)
        Future<Void> onEvent(TestEvent event) {
            return Future.failedFuture(new IllegalStateException("This consumer always fails"));
        }
    }

    public static class InvalidConsumer {
        @KinoticConsumer
        void onEvent(TestEvent event, String unexpectedParameter) {
        }
    }

    public static class ValueReturningConsumer {
        @KinoticConsumer
        String onEvent(TestEvent event) {
            return "consumers must return void or a Future";
        }
    }
}
