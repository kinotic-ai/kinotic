package org.kinotic.github.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.internal.EventFabricBeanPostProcessor;
import org.kinotic.core.internal.api.event.DefaultEventBusService;
import org.kinotic.core.internal.api.event.EventMessageCodec;
import org.kinotic.core.internal.api.event.fabric.EventFabric;
import org.kinotic.domain.api.model.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;
import org.kinotic.github.api.model.GitHubProjectEvent;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link DefaultGitHubProjectEventService} over the real event fabric: events
 * emitted anywhere reach it through the bus, and the stream each participant receives is narrowed
 * to what that participant may see. Uses the real {@link SecurityContext} by binding participants
 * to real Vert.x contexts.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
public class DefaultGitHubProjectEventServiceTest {

    private Vertx vertx;
    private SecurityContext securityContext;
    private DefaultGitHubProjectEventService service;
    private TestProjectEventProducer producer;

    @BeforeEach
    public void setUp() {
        // SecurityContext registers its ContextLocal in static init, which must happen before any
        // Vertx instance exists
        securityContext = new SecurityContext();
        vertx = Vertx.vertx();
        JsonMapper jsonMapper = JsonMapper.builder().build();
        vertx.eventBus().registerCodec(new EventMessageCodec(jsonMapper));
        vertx.eventBus().codecSelector(body -> body instanceof Event ? EventMessageCodec.NAME : null);
        EventFabric eventFabric = new EventFabric(new DefaultEventBusService(null, vertx),
                                                  jsonMapper,
                                                  ReactiveAdapterRegistry.getSharedInstance());
        EventFabricBeanPostProcessor postProcessor = new EventFabricBeanPostProcessor(eventFabric);

        service = new DefaultGitHubProjectEventService(securityContext);
        postProcessor.postProcessAfterInitialization(service, "gitHubProjectEventService");
        producer = new TestProjectEventProducer();
        postProcessor.postProcessAfterInitialization(producer, "producer");
    }

    @AfterEach
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void organizationParticipantSeesOnlyItsOrganizationsEvents() throws Exception {
        Flux<GitHubProjectEvent> events = eventsAs(DefaultOrganizationParticipant.builder()
                                                                                 .id("user-1")
                                                                                 .organizationId("acme")
                                                                                 .build());
        List<GitHubProjectEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        events.subscribe(event -> {
            received.add(event);
            latch.countDown();
        });

        producer.emit(projectEvent("acme", "project-1"));
        producer.emit(projectEvent("other-org", "project-2"));
        producer.emit(projectEvent("acme", "project-3"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "did not receive the organization's events");
        assertEquals(List.of("project-1", "project-3"),
                     received.stream().map(GitHubProjectEvent::getProjectId).toList());
    }

    @Test
    public void systemParticipantSeesEveryOrganizationsEvents() throws Exception {
        Flux<GitHubProjectEvent> events = eventsAs(DefaultSystemParticipant.builder()
                                                                           .id("system")
                                                                           .build());
        List<GitHubProjectEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        events.subscribe(event -> {
            received.add(event);
            latch.countDown();
        });

        producer.emit(projectEvent("acme", "project-1"));
        producer.emit(projectEvent("other-org", "project-2"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "system participant did not receive all events");
        assertEquals(List.of("project-1", "project-2"),
                     received.stream().map(GitHubProjectEvent::getProjectId).toList());
    }

    @Test
    public void missingParticipantIsDenied() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> eventsAs(null));
        assertInstanceOf(AuthorizationException.class, e.getCause());
    }

    // Calls events() on a real Vert.x context carrying the participant, the way an RPC invocation does
    private Flux<GitHubProjectEvent> eventsAs(Participant participant) throws Exception {
        CompletableFuture<Flux<GitHubProjectEvent>> future = new CompletableFuture<>();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            try {
                if (participant != null) {
                    securityContext.setParticipant(context, participant);
                }
                future.complete(service.events());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }

    private static GitHubProjectEvent projectEvent(String organizationId, String projectId) {
        return new GitHubProjectEvent().setOrganizationId(organizationId)
                                       .setProjectId(projectId);
    }

    // Emits the same event type as DefaultGitHubWebhookEventService's emitter, so the consumer
    // under test binds to the identical fabric address
    public static class TestProjectEventProducer {
        private final Sinks.Many<GitHubProjectEvent> sink = Sinks.many().multicast().directBestEffort();

        @Emitter
        Flux<GitHubProjectEvent> events() {
            return sink.asFlux();
        }

        void emit(GitHubProjectEvent event) {
            sink.tryEmitNext(event);
        }
    }
}
