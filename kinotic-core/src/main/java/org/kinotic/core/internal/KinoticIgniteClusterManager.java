package org.kinotic.core.internal;

import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.api.event.ServiceListenerChange;
import org.kinotic.core.api.event.ServiceListenerContinuityLost;
import org.kinotic.core.api.event.ServiceListenerEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.cache.Cache;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link IgniteClusterManager} that additionally provides a {@link Flux} of {@link ListenerStatus} for
 * any event bus address, fed by the registration updates it already receives for message routing.
 * Monitoring an address therefore costs a local map entry, no matter how many addresses are monitored or
 * how often monitors come and go. All monitor signals are delivered on a vertx context, never on the
 * cluster threads that observe registration changes.
 *
 * Created by Navid on 7/13/26
 */
@Slf4j
public class KinoticIgniteClusterManager extends IgniteClusterManager {

    // The cache vertx-ignite keeps event bus subscriptions in, keyed by address with the set of
    // registrations as the value (see SubsMapHelper)
    private static final String VERTX_SUBSCRIPTION_CACHE = "__vertx.subs";

    private static final String SERVICE_ADDRESS_PREFIX = EventConstants.SERVICE_DESTINATION_SCHEME + "://";

    private final Ignite ignite;
    private final Map<String, AddressMonitor> monitors = new ConcurrentHashMap<>();
    // Hot sink shared by every serviceListenerEventsFlux subscriber; never terminates
    private final Sinks.Many<ServiceListenerEvent> serviceListenerSink = Sinks.many().multicast().directBestEffort();
    private volatile Vertx vertx;
    private volatile Context deliveryContext;

    public KinoticIgniteClusterManager(Ignite ignite) {
        super(ignite);
        this.ignite = ignite;
    }

    @Override
    public void init(Vertx vertx) {
        super.init(vertx);
        this.vertx = vertx;
    }

    // One shared context all monitors deliver on, so subscriber chains never run on the cluster
    // threads that observe registration changes. Created lazily on first subscription — init runs
    // while vertx is still bootstrapping.
    private Context deliveryContext() {
        Context context = deliveryContext;
        if(context == null){
            synchronized(this){
                if(deliveryContext == null){
                    deliveryContext = vertx.getOrCreateContext();
                }
                context = deliveryContext;
            }
        }
        return context;
    }

    @Override
    public void registrationListener(RegistrationListener registrationListener) {
        // Wrap the listener vertx core supplies, so registration updates reach both vertx's node
        // selector for message routing and any active monitors
        super.registrationListener(new RegistrationListener() {
            @Override
            public boolean wantsUpdatesFor(String address) {
                return monitors.containsKey(address)
                        || (serviceListenerSink.currentSubscriberCount() > 0 && address.startsWith(SERVICE_ADDRESS_PREFIX))
                        || registrationListener.wantsUpdatesFor(address);
            }

            @Override
            public void registrationsUpdated(RegistrationUpdateEvent event) {
                // an update fired for a monitor is not forwarded unless the wrapped listener asked
                // for the address, matching what the cluster manager would deliver without this wrapper
                if(registrationListener.wantsUpdatesFor(event.address())){
                    registrationListener.registrationsUpdated(event);
                }
                AddressMonitor monitor = monitors.get(event.address());
                if(monitor != null){
                    monitor.emit(statusOf(event.registrations()));
                }
                if(event.address().startsWith(SERVICE_ADDRESS_PREFIX) && serviceListenerSink.currentSubscriberCount() > 0){
                    emitServiceListenerEvent(new ServiceListenerChange(event.address(), statusOf(event.registrations())));
                }
            }

            @Override
            public void registrationsLost() {
                registrationListener.registrationsLost();
                // Continuity of registration updates was lost, so the current state of every
                // monitored address must be re-queried
                monitors.keySet().forEach(address -> refresh(address, false));
                // The service address space cannot be re-queried address by address; subscribers
                // rebuild their baseline from a registeredServiceAddresses snapshot
                emitServiceListenerEvent(new ServiceListenerContinuityLost());
            }
        });
    }

    /**
     * A {@link Flux} of {@link ListenerStatus} for the given address, shared between all subscribers
     * for the same address. Emits the current status on subscribe and the resulting status of every
     * registration change after that, so consecutive duplicates are possible.
     * @param address the event bus address to monitor
     * @return the status flux
     */
    public Flux<ListenerStatus> statusFlux(String address) {
        return Flux.defer(() -> {
            Context context = deliveryContext();
            AddressMonitor monitor = monitors.compute(address, (a, existing) -> {
                AddressMonitor m = existing != null ? existing : new AddressMonitor(context);
                m.subscribers++;
                return m;
            });
            // Query the current status only after the monitor is visible to registrationsUpdated, so a
            // registration change between the query and the first update event cannot be missed
            refresh(address, true);
            return monitor.sink.asFlux()
                               .doFinally(signal -> monitors.computeIfPresent(address, (a, m) -> --m.subscribers == 0 ? null : m));
        });
    }

    /**
     * A hot {@link Flux} of {@link ServiceListenerEvent}s for every service ({@code srv://}) address, shared
     * between all subscribers. Carries only events — snapshot with {@link #registeredServiceAddresses} to
     * establish a baseline, and rebuild it whenever a {@link ServiceListenerContinuityLost} arrives.
     * @return the event flux
     */
    public Flux<ServiceListenerEvent> serviceListenerEventsFlux() {
        return serviceListenerSink.asFlux();
    }

    private void emitServiceListenerEvent(ServiceListenerEvent event) {
        deliveryContext().runOnContext(v -> {
            Sinks.EmitResult result = serviceListenerSink.tryEmitNext(event);
            if(result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER){
                log.warn("Failed to emit ServiceListenerEvent {}: {}", event, result);
            }
        });
    }

    /**
     * Snapshots every service ({@code srv://}) address that currently has a registered listener. Blocking.
     * @return the set of registered service addresses
     */
    public Set<String> registeredServiceAddresses() {
        IgniteCache<String, Set<IgniteRegistrationInfo>> cache = ignite.cache(VERTX_SUBSCRIPTION_CACHE);
        if(cache == null){
            throw new IllegalStateException("The vertx subscription cache is not available");
        }
        Set<String> addresses = new HashSet<>();
        // an entry's existence implies at least one registration: SubsMapHelper's entry processors
        // remove the key atomically when its registration set empties
        for(Cache.Entry<String, Set<IgniteRegistrationInfo>> entry : cache){
            String address = entry.getKey();
            if(address.startsWith(SERVICE_ADDRESS_PREFIX)){
                addresses.add(address);
            }
        }
        return addresses;
    }

    private void refresh(String address, boolean seed) {
        Promise<List<RegistrationInfo>> promise = Promise.promise();
        getRegistrations(address, promise);
        promise.future().onComplete(ar -> {
            AddressMonitor monitor = monitors.get(address);
            if(monitor == null){
                return;
            }
            if(ar.succeeded()){
                ListenerStatus status = statusOf(ar.result());
                if(seed){
                    monitor.seed(status);
                }else{
                    monitor.emit(status);
                }
            }else{
                log.error("Failed to query registrations for monitored address {}", address, ar.cause());
                monitor.fail(ar.cause());
            }
        });
    }

    private static ListenerStatus statusOf(List<RegistrationInfo> registrations) {
        return registrations == null || registrations.isEmpty() ? ListenerStatus.INACTIVE : ListenerStatus.ACTIVE;
    }

    /**
     * Per-address sink plus the subscriber count used to remove idle entries. Emissions are serialized
     * on the delivery context; the emitted flag keeps a seed scheduled behind an update event from
     * overwriting the newer status with a stale one.
     */
    private static class AddressMonitor {

        final Sinks.Many<ListenerStatus> sink = Sinks.many().replay().latest();
        int subscribers; // mutated only inside monitors.compute* blocks for this address
        private final Context deliveryContext;
        private boolean emitted; // touched only on the delivery context

        AddressMonitor(Context deliveryContext) {
            this.deliveryContext = deliveryContext;
        }

        void emit(ListenerStatus status) {
            deliveryContext.runOnContext(v -> {
                emitted = true;
                tryEmit(status);
            });
        }

        void seed(ListenerStatus status) {
            deliveryContext.runOnContext(v -> {
                if(!emitted){
                    emitted = true;
                    tryEmit(status);
                }
            });
        }

        void fail(Throwable throwable) {
            deliveryContext.runOnContext(v -> sink.tryEmitError(throwable));
        }

        private void tryEmit(ListenerStatus status) {
            Sinks.EmitResult result = sink.tryEmitNext(status);
            if(result.isFailure()){
                log.warn("Failed to emit ListenerStatus {}: {}", status, result);
            }
        }
    }

}
