package org.kinotic.core.internal.api.event;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link Consumer} methods of this node, by event type. Each event type has one bus consumer on the type's topic
 * address, whose registration is how the cluster knows the node consumes the type, and every event published to the
 * address is handed to every {@link Consumer} method of the type here.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerMethodRegistry {

    private final EventBusService eventBusService;
    private final JsonMapper jsonMapper;
    private final Vertx vertx;

    // Keyed by bean identity: bean equals() must not merge two beans' registrations
    private final Map<Object, List<ConsumerRegistration>> registrations = Collections.synchronizedMap(new IdentityHashMap<>());

    // One bus consumer per event type per node, shared by every @Consumer method of that type; keyed by the type's
    // name, which names its topic address
    private final Map<String, EventTypeConsumers> consumersByType = new ConcurrentHashMap<>();

    /**
     * The topic address the events of a type are published to and consumed from.
     * @param eventType the fully qualified name of the event type
     * @return the topic {@link CRI} of the type
     */
    static CRI topicOf(String eventType) {
        return CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, eventType);
    }

    /**
     * Registers the bean's {@link Consumer} method to receive every event of its parameter type.
     * @param bean hosting the consumer method
     * @param method the {@link Consumer} method
     * @return a {@link Future} that completes when the type's bus consumer is registered with the cluster
     * @throws IllegalArgumentException when the method does not satisfy the {@link Consumer} contract
     */
    public Future<Void> register(Object bean, Method method) {
        if(method.getParameterCount() != 1){
            throw new IllegalArgumentException("@Consumer method must take exactly one parameter, the event: " + method);
        }
        if(method.getReturnType() != void.class && !Future.class.isAssignableFrom(method.getReturnType())){
            throw new IllegalArgumentException("@Consumer method must return void or a Future: " + method);
        }
        Class<?> eventType = resolveEventType(ResolvableType.forMethodParameter(method, 0), method);

        ReflectionUtils.makeAccessible(method);
        ConsumerMethod consumerMethod = new ConsumerMethod(bean, method);

        // computeIfAbsent makes creating the type's bus consumer atomic; the CopyOnWriteArrayList makes adding a
        // method safe against a concurrent delivery
        EventTypeConsumers consumers = consumersByType.computeIfAbsent(eventType.getName(), name -> createConsumers(eventType));
        consumers.methods.add(consumerMethod);
        registrations.computeIfAbsent(bean, b -> new ArrayList<>())
                     .add(new ConsumerRegistration(eventType.getName(), consumerMethod));
        log.info("Registered @Consumer of {} at {}", eventType.getName(), method);
        return consumers.busConsumer.completion();
    }

    /**
     * @param bean to check
     * @return true when the bean has consumer methods that {@link #unregister(Object)} must remove
     */
    public boolean isRegistered(Object bean) {
        return registrations.containsKey(bean);
    }

    /**
     * Removes every {@link Consumer} method of the bean, unregistering a type's bus consumer once its last method is
     * gone.
     * @param bean whose consumer methods are removed
     */
    public void unregister(Object bean) {
        List<ConsumerRegistration> removed = registrations.remove(bean);
        if(removed == null){
            return;
        }
        for(ConsumerRegistration registration : removed){
            consumersByType.computeIfPresent(registration.eventType(), (type, consumers) -> {
                consumers.methods.remove(registration.method());
                EventTypeConsumers ret = consumers;
                if(consumers.methods.isEmpty()){
                    consumers.busConsumer.unregister();
                    ret = null;
                }
                return ret;
            });
        }
    }

    /**
     * Hands an event published with {@link org.kinotic.core.api.event.EventService#publishConfirmed(Object)} to
     * every {@link Consumer} method of its type on this node, on a Vert.x event loop.
     * @param eventType the fully qualified name of the event's type
     * @param payload the event as JSON
     * @return a {@link Future} that completes once every consumer has handled the event, at once when this node has
     *         none, and fails when the event cannot be read or a consumer fails
     */
    public Future<Void> deliverConfirmed(String eventType, byte[] payload) {
        EventTypeConsumers consumers = consumersByType.get(eventType);
        Future<Void> ret;
        if(consumers == null){
            ret = Future.succeededFuture();
        }else{
            Promise<Void> promise = Promise.promise();
            Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> {
                try {
                    Object event = jsonMapper.readValue(payload, consumers.eventType);
                    List<Future<Void>> handled = new ArrayList<>();
                    for(ConsumerMethod method : consumers.methods){
                        handled.add(invoke(method, event));
                    }
                    Future.all(handled).<Void>mapEmpty().onComplete(promise);
                } catch (Exception e) {
                    promise.fail(e);
                }
            });
            ret = promise.future();
        }
        return ret;
    }

    private EventTypeConsumers createConsumers(Class<?> eventType) {
        EventTypeConsumers consumers = new EventTypeConsumers(eventType, eventBusService.listen(topicOf(eventType.getName())));
        consumers.busConsumer
                .handler(event -> deliver(consumers, event))
                .exceptionHandler(error -> log.error("Bus consumer for {} signaled an error", eventType.getName(), error));
        return consumers;
    }

    private void deliver(EventTypeConsumers consumers, Event<byte[]> event) {
        Object element;
        try {
            element = jsonMapper.readValue(event.data(), consumers.eventType);
        } catch (Exception e) {
            log.error("Failed to deserialize {} event, dropping it", consumers.eventType.getName(), e);
            return;
        }
        // One consumer failing must not affect delivery to the others
        for(ConsumerMethod method : consumers.methods){
            invoke(method, element).onFailure(error -> log.error("@Consumer method failed for {} event: {}",
                                                                 consumers.eventType.getName(), method.method(), error));
        }
    }

    private static Future<Void> invoke(ConsumerMethod method, Object event) {
        Future<Void> ret;
        try {
            Object result = ReflectionUtils.invokeMethod(method.method(), method.bean(), event);
            ret = result instanceof Future<?> future ? future.mapEmpty() : Future.succeededFuture();
        } catch (Throwable t) {
            ret = Future.failedFuture(t);
        }
        return ret;
    }

    private static Class<?> resolveEventType(ResolvableType type, Method method) {
        Class<?> eventType = type.resolve();
        if(eventType == null || eventType == Object.class){
            throw new IllegalArgumentException("Event type must be a concrete class, could not resolve it for: " + method);
        }
        if(eventType.isInterface() || Modifier.isAbstract(eventType.getModifiers())){
            throw new IllegalArgumentException("Event type must be a concrete class but " + eventType.getName()
                                                       + " is not, for: " + method);
        }
        return eventType;
    }

}
