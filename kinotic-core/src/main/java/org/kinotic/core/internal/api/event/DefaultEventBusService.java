

package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.tracing.TracingPolicy;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.api.event.ServiceListenerEvent;
import org.kinotic.core.internal.api.ignite.KinoticIgniteClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link EventBusService} using the vertx {@link io.vertx.core.eventbus.EventBus} as a backend
 *
 *
 * Created by navid on 11/5/19
 */
@Component
public class DefaultEventBusService implements EventBusService {

    private final KinoticIgniteClusterManager clusterManager;
    private final Vertx vertx;
    // Addresses this node currently has a local consumer for, reference counted. Populated only by
    // listen() on this node, so it holds purely local registrations and lets sends prefer local delivery.
    private final Map<String, Integer> localListenerCounts = new ConcurrentHashMap<>();

    public DefaultEventBusService(@Autowired(required = false)
                                  KinoticIgniteClusterManager clusterManager,
                                  Vertx vertx) {
        this.clusterManager = clusterManager;
        this.vertx = vertx;
    }

    @Override
    public Future<Boolean> isAnybodyListening(CRI cri) {
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        Promise<List<RegistrationInfo>> promise = Promise.promise();
        clusterManager.getRegistrations(cri.baseResource(), promise);
        return promise.future().map(registrations -> registrations != null && !registrations.isEmpty());
    }

    @Override
    public EventConsumer listen(CRI cri) {
        Validate.notNull(cri, "The cri must be provided");
        String address = cri.baseResource();
        MessageConsumer<Event<byte[]>> consumer = vertx.eventBus().consumer(address);
        localListenerCounts.merge(address, 1, Integer::sum);
        return new DefaultEventConsumer(consumer,
                                        () -> localListenerCounts.computeIfPresent(address, (k, count) -> count == 1 ? null : count - 1));
    }

    @Override
    public Flux<ListenerStatus> monitorListenerStatus(CRI cri) {
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        // Every registration change on the address emits its resulting status, dedupe to transitions
        return clusterManager.statusFlux(cri.baseResource())
                             .distinctUntilChanged();
    }

    @Override
    public Flux<ServiceListenerEvent> monitorServiceListenerEvents() {
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        return clusterManager.serviceListenerEventsFlux();
    }

    @Override
    public Future<Set<String>> activeServiceAddresses() {
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        // scanning the cluster registrations is blocking work
        return vertx.executeBlocking(clusterManager::registeredServiceAddresses);
    }

    @Override
    public void send(Event<byte[]> event) {
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        vertx.eventBus().send(baseResource,
                              event,
                              deliveryOptions);
    }

    @Override
    public Future<Void> sendWithAck(Event<byte[]> event) {
        Validate.notNull(event, "Event must not be null");
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        return vertx.eventBus()
                    .request(baseResource,
                             event,
                             deliveryOptions)
                    .mapEmpty();
    }

    DeliveryOptions createDeliveryOptions(Event<?> event, String baseResource){
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setTracingPolicy(TracingPolicy.IGNORE);
        // When this node already hosts the target service, pin the request to the local handler rather
        // than letting Vert.x round-robin to a remote node that would proxy the same backend.
        if(EventConstants.SERVICE_DESTINATION_SCHEME.equals(event.cri().scheme())
               && localListenerCounts.containsKey(baseResource)){
            deliveryOptions.setLocalOnly(true);
        }
        return deliveryOptions;
    }

}
