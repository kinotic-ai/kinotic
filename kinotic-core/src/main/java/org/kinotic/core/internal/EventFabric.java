package org.kinotic.core.internal;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wires {@link Emitter} and {@link Consumer} methods to the clustered event bus: an emitter's flux is
 * subscribed once and each element published to the topic address derived from the element type, and
 * one bus consumer per event type per node dispatches incoming events to every {@link Consumer}
 * method of that type. {@link EventFabricBeanPostProcessor} drives wiring from the bean lifecycle.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventFabric {

    private final EventBusService eventBusService;
    private final JsonMapper jsonMapper;

    // Keyed by bean identity: bean equals() must not merge two beans' wirings
    private final Map<Object, BeanWiring> wirings = Collections.synchronizedMap(new IdentityHashMap<>());

    // One bus consumer per event type per node, shared by every @Consumer method of that type
    private final Map<Class<?>, Downlink> downlinks = new ConcurrentHashMap<>();

    /**
     * Subscribes to the bean's {@link Emitter} flux and publishes every element cluster-wide.
     * @param bean hosting the emitter method
     * @param method the {@link Emitter} method
     * @throws IllegalArgumentException when the method does not satisfy the {@link Emitter} contract
     */
    public void wireEmitter(Object bean, Method method) {
        if(method.getParameterCount() != 0){
            throw new IllegalArgumentException("@Emitter method must take no parameters: " + method);
        }
        if(!Flux.class.isAssignableFrom(method.getReturnType())){
            throw new IllegalArgumentException("@Emitter method must return a Flux: " + method);
        }
        Class<?> eventType = resolveEventType(ResolvableType.forMethodReturnType(method).getGeneric(0), method);

        ReflectionUtils.makeAccessible(method);
        Flux<?> flux = (Flux<?>) ReflectionUtils.invokeMethod(method, bean);
        if(flux == null){
            throw new IllegalArgumentException("@Emitter method returned null: " + method);
        }

        CRI cri = CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, eventType.getName());
        Disposable uplink = flux.subscribe(element -> publishElement(cri, eventType, element),
                                           error -> log.error("@Emitter flux signaled an error, the uplink for {} is dead. From: {}",
                                                              eventType.getName(), method, error));
        wiringFor(bean).uplinks.add(uplink);
        log.info("Registered @Emitter of {} from {}", eventType.getName(), method);
    }

    /**
     * Registers the bean's {@link Consumer} method to receive every event of its parameter type.
     * @param bean hosting the consumer method
     * @param method the {@link Consumer} method
     * @return a {@link Future} that completes when the underlying bus consumer is registered
     * @throws IllegalArgumentException when the method does not satisfy the {@link Consumer} contract
     */
    public Future<Void> wireConsumer(Object bean, Method method) {
        if(method.getParameterCount() != 1){
            throw new IllegalArgumentException("@Consumer method must take exactly one parameter, the event: " + method);
        }
        if(method.getReturnType() != void.class){
            throw new IllegalArgumentException("@Consumer method must return void: " + method);
        }
        Class<?> eventType = resolveEventType(ResolvableType.forMethodParameter(method, 0), method);

        ReflectionUtils.makeAccessible(method);
        MethodTarget target = new MethodTarget(bean, method);

        // computeIfAbsent makes downlink creation atomic; the CopyOnWriteArrayList makes target
        // mutation safe against concurrent dispatch
        Downlink downlink = downlinks.computeIfAbsent(eventType, this::createDownlink);
        downlink.targets.add(target);
        wiringFor(bean).consumerTargets.add(new ConsumerRegistration(eventType, target));
        log.info("Registered @Consumer of {} at {}", eventType.getName(), method);
        return downlink.busConsumer.completion();
    }

    /**
     * @param bean to check
     * @return true when the bean has emitter or consumer wiring that {@link #unwire(Object)} must tear down
     */
    public boolean isWired(Object bean) {
        return wirings.containsKey(bean);
    }

    /**
     * Tears down every wiring created for the bean: emitter subscriptions are disposed and consumer
     * targets removed, unregistering a type's bus consumer once its last target is gone.
     * @param bean whose wiring is removed
     */
    public void unwire(Object bean) {
        BeanWiring wiring = wirings.remove(bean);
        if(wiring == null){
            return;
        }
        wiring.uplinks.forEach(Disposable::dispose);
        for(ConsumerRegistration registration : wiring.consumerTargets){
            downlinks.computeIfPresent(registration.eventType(), (type, downlink) -> {
                downlink.targets.remove(registration.target());
                Downlink ret = downlink;
                if(downlink.targets.isEmpty()){
                    downlink.busConsumer.unregister();
                    ret = null;
                }
                return ret;
            });
        }
    }

    private Downlink createDownlink(Class<?> eventType) {
        CRI cri = CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, eventType.getName());
        Downlink downlink = new Downlink(eventBusService.listen(cri));
        downlink.busConsumer
                .handler(event -> dispatch(eventType, downlink, event))
                .exceptionHandler(error -> log.error("Bus consumer for {} signaled an error", eventType.getName(), error));
        return downlink;
    }

    private void dispatch(Class<?> eventType, Downlink downlink, Event<byte[]> event) {
        Object element;
        try {
            element = jsonMapper.readValue(event.data(), eventType);
        } catch (Exception e) {
            log.error("Failed to deserialize {} event, dropping it", eventType.getName(), e);
            return;
        }
        // One consumer failing must not affect delivery to the others
        for(MethodTarget target : downlink.targets){
            try {
                ReflectionUtils.invokeMethod(target.method(), target.bean(), element);
            } catch (Throwable t) {
                log.error("@Consumer method threw for {} event: {}", eventType.getName(), target.method(), t);
            }
        }
    }

    private void publishElement(CRI cri, Class<?> eventType, Object element) {
        try {
            byte[] payload = jsonMapper.writeValueAsBytes(element);
            Metadata metadata = Metadata.create();
            metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_JSON);
            eventBusService.publish(Event.create(cri, metadata, payload));
        } catch (Exception e) {
            // At-most-once contract: log and drop, the emitter's flux must never be poisoned
            log.error("Failed to publish {} event, dropping it", eventType.getName(), e);
        }
    }

    private BeanWiring wiringFor(Object bean) {
        return wirings.computeIfAbsent(bean, b -> new BeanWiring());
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

    private static class BeanWiring {
        private final List<Disposable> uplinks = new ArrayList<>();
        private final List<ConsumerRegistration> consumerTargets = new ArrayList<>();
    }

    private record ConsumerRegistration(Class<?> eventType, MethodTarget target) {}

    private record MethodTarget(Object bean, Method method) {}

    private static class Downlink {
        private final EventConsumer busConsumer;
        private final List<MethodTarget> targets = new CopyOnWriteArrayList<>();

        private Downlink(EventConsumer busConsumer) {
            this.busConsumer = busConsumer;
        }
    }
}
