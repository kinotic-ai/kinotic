package org.kinotic.core.internal.api.event;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.annotations.KinoticConsumer;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventDelivery;
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
import java.util.concurrent.TimeUnit;

/**
 * The {@link KinoticConsumer} methods of this node, by event type and {@link EventDelivery}. Each pair has one bus
 * consumer on its address, whose registration is how the cluster knows the node consumes the type with that delivery.
 * Every event published to a {@link EventDelivery#BEST_EFFORT} address is handed to the type's best-effort methods,
 * and every event {@link #deliverConfirmed(String, byte[])} receives to the type's {@link EventDelivery#CONFIRMED}
 * methods.
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

    /**
     * How long {@link #deliverConfirmed(String, byte[])} waits for this node's confirmed consumers to handle an event.
     */
    static final long CONFIRMED_TIMEOUT_MS = 4000L;

    // The scope of the address CONFIRMED consumers register on, so they register apart from the best-effort ones
    private static final String CONFIRMED_SCOPE = "confirmed";

    // One bus consumer per event type and delivery per node, shared by every @KinoticConsumer method of that pair;
    // keyed by the pair's address
    private final Map<String, EventTypeConsumers> consumersByAddress = new ConcurrentHashMap<>();

    /**
     * The address the consumers of an event type with a delivery register on.
     * @param eventType the fully qualified name of the event type
     * @param delivery the delivery the consumers declared
     * @return the topic {@link CRI} for the type and delivery
     */
    static CRI addressOf(String eventType, EventDelivery delivery) {
        CRI ret;
        if(delivery == EventDelivery.CONFIRMED){
            ret = CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, CONFIRMED_SCOPE, eventType);
        }else{
            ret = CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, eventType);
        }
        return ret;
    }

    /**
     * Registers the bean's {@link KinoticConsumer} method to receive every event of its parameter type, as its
     * {@link KinoticConsumer#delivery()} declares.
     * @param bean hosting the consumer method
     * @param method the {@link KinoticConsumer} method
     * @return a {@link Future} that completes when the bus consumer of the type and delivery is registered with the
     *         cluster
     * @throws IllegalArgumentException when the method does not satisfy the {@link KinoticConsumer} contract
     */
    public Future<Void> register(Object bean, Method method) {
        if(method.getParameterCount() != 1){
            throw new IllegalArgumentException("@KinoticConsumer method must take exactly one parameter, the event: " + method);
        }
        if(method.getReturnType() != void.class && !Future.class.isAssignableFrom(method.getReturnType())){
            throw new IllegalArgumentException("@KinoticConsumer method must return void or a Future: " + method);
        }
        Class<?> eventType = resolveEventType(ResolvableType.forMethodParameter(method, 0), method);
        String address = addressOf(eventType.getName(), method.getAnnotation(KinoticConsumer.class).delivery()).baseResource();

        ReflectionUtils.makeAccessible(method);
        ConsumerMethod consumerMethod = new ConsumerMethod(bean, method);

        // the method is added inside compute, so an unregister of the address's last method cannot remove the bus
        // consumer between the lookup and the add; the CopyOnWriteArrayList makes the add safe against a delivery
        EventTypeConsumers consumers = consumersByAddress.compute(address, (key, existing) -> {
            EventTypeConsumers ret = existing != null ? existing : createConsumers(eventType, address);
            ret.methods.add(consumerMethod);
            return ret;
        });
        registrations.computeIfAbsent(bean, b -> new ArrayList<>())
                     .add(new ConsumerRegistration(address, consumerMethod));
        log.info("Registered @KinoticConsumer of {} on {} at {}", eventType.getName(), address, method);
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
     * Removes every {@link KinoticConsumer} method of the bean, unregistering an address's bus consumer once its last
     * method is gone.
     * @param bean whose consumer methods are removed
     */
    public void unregister(Object bean) {
        List<ConsumerRegistration> removed = registrations.remove(bean);
        if(removed == null){
            return;
        }
        for(ConsumerRegistration registration : removed){
            consumersByAddress.computeIfPresent(registration.address(), (address, consumers) -> {
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
     * Hands an event to every {@link EventDelivery#CONFIRMED} {@link KinoticConsumer} method of its type on this node,
     * on a Vert.x event loop.
     * @param eventType the fully qualified name of the event's type
     * @param payload the event as JSON
     * @return a {@link Future} that completes once every confirmed consumer has handled the event, at once when this
     *         node has none, and fails when the event cannot be read, a consumer fails, or the consumers have not all
     *         handled it within {@link #CONFIRMED_TIMEOUT_MS}
     */
    public Future<Void> deliverConfirmed(String eventType, byte[] payload) {
        EventTypeConsumers consumers = consumersByAddress.get(addressOf(eventType, EventDelivery.CONFIRMED).baseResource());
        Future<Void> ret;
        if(consumers == null){
            ret = Future.succeededFuture();
        }else{
            Promise<Void> promise = Promise.promise();
            Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> dispatch(consumers, payload).onComplete(promise));
            ret = promise.future().timeout(CONFIRMED_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        return ret;
    }

    // publish sends only to the best-effort address; the bus consumer of a confirmed address receives nothing over the
    // bus, and its registration tells publish which nodes to dispatch the event to
    private EventTypeConsumers createConsumers(Class<?> eventType, String address) {
        EventTypeConsumers consumers = new EventTypeConsumers(eventType, eventBusService.listen(CRI.create(address)));
        consumers.busConsumer
                .handler(event -> dispatch(consumers, event.data()))
                .exceptionHandler(error -> log.error("Bus consumer for {} signaled an error", eventType.getName(), error));
        return consumers;
    }

    // reads the event and hands it to every method, each failure logged; one method failing does not affect delivery
    // to the others. The future completes once every method has handled the event, and fails when it cannot be read
    // or a method fails
    private Future<Void> dispatch(EventTypeConsumers consumers, byte[] payload) {
        Future<Void> ret;
        try {
            Object event = jsonMapper.readValue(payload, consumers.eventType);
            List<Future<Void>> handled = new ArrayList<>();
            for(ConsumerMethod method : consumers.methods){
                handled.add(invoke(method, event).onFailure(error -> log.error("@KinoticConsumer method failed for {} event: {}",
                                                                               consumers.eventType.getName(), method.method(), error)));
            }
            ret = Future.all(handled).mapEmpty();
        } catch (Exception e) {
            log.error("Failed to deserialize {} event, dropping it", consumers.eventType.getName(), e);
            ret = Future.failedFuture(e);
        }
        return ret;
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
