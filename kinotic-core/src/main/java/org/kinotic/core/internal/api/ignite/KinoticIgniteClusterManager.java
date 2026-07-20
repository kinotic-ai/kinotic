package org.kinotic.core.internal.api.ignite;

import io.vertx.core.Promise;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.ListenerChange;
import org.kinotic.core.api.event.ListenerStatus;
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
 * how often monitors come and go.
 *
 * Created by Navid on 7/13/26
 */
@Slf4j
public class KinoticIgniteClusterManager extends IgniteClusterManager {

    // The cache vertx-ignite keeps event bus subscriptions in, keyed by (address, node) registration
    private static final String VERTX_SUBSCRIPTION_CACHE = "__vertx.subs";

    private static final String SERVICE_ADDRESS_PREFIX = EventConstants.SERVICE_DESTINATION_SCHEME + "://";

    private final Ignite ignite;
    private final Map<String, AddressMonitor> monitors = new ConcurrentHashMap<>();
    // Guards serviceMonitor creation/removal and its subscriber count
    private final Object serviceMonitorLock = new Object();
    private volatile ServiceMonitor serviceMonitor;

    public KinoticIgniteClusterManager(Ignite ignite) {
        super(ignite);
        this.ignite = ignite;
    }

    @Override
    public void registrationListener(RegistrationListener registrationListener) {
        // Wrap the listener vertx core supplies, so registration updates reach both vertx's node
        // selector for message routing and any active monitors
        super.registrationListener(new RegistrationListener() {
            @Override
            public boolean wantsUpdatesFor(String address) {
                return monitors.containsKey(address)
                        || (serviceMonitor != null && address.startsWith(SERVICE_ADDRESS_PREFIX))
                        || registrationListener.wantsUpdatesFor(address);
            }

            @Override
            public void registrationsUpdated(RegistrationUpdateEvent event) {
                registrationListener.registrationsUpdated(event);
                AddressMonitor monitor = monitors.get(event.address());
                if(monitor != null){
                    monitor.emit(statusOf(event.registrations()));
                }
                ServiceMonitor changeMonitor = serviceMonitor;
                if(changeMonitor != null && event.address().startsWith(SERVICE_ADDRESS_PREFIX)){
                    changeMonitor.emit(new ListenerChange(event.address(), statusOf(event.registrations())));
                }
            }

            @Override
            public void registrationsLost() {
                registrationListener.registrationsLost();
                // Continuity of registration updates was lost, so the current state of every
                // monitored address must be re-queried
                monitors.keySet().forEach(address -> refresh(address, false));
                // The service monitor cannot be re-queried address by address; subscribers recover by
                // resubscribing and re-snapshotting via registeredServiceAddresses
                ServiceMonitor changeMonitor = serviceMonitor;
                if(changeMonitor != null){
                    changeMonitor.fail(new IllegalStateException("Registration update continuity was lost"));
                }
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
            AddressMonitor monitor = monitors.compute(address, (a, existing) -> {
                AddressMonitor m = existing != null ? existing : new AddressMonitor();
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
     * A {@link Flux} of {@link ListenerChange}s for every service ({@code srv://}) address, shared between all
     * subscribers. Carries only changes — snapshot with {@link #registeredServiceAddresses} to establish a
     * baseline. Errors when registration update continuity is lost; subscribers recover by resubscribing and
     * re-snapshotting.
     * @return the change flux
     */
    public Flux<ListenerChange> serviceChangesFlux() {
        return Flux.defer(() -> {
            ServiceMonitor monitor;
            synchronized(serviceMonitorLock){
                monitor = serviceMonitor != null ? serviceMonitor : (serviceMonitor = new ServiceMonitor());
                monitor.subscribers++;
            }
            return monitor.sink.asFlux()
                               .doFinally(signal -> {
                                   synchronized(serviceMonitorLock){
                                       if(--monitor.subscribers == 0 && serviceMonitor == monitor){
                                           serviceMonitor = null;
                                       }
                                   }
                               });
        });
    }

    /**
     * Snapshots every service ({@code srv://}) address that currently has a registered listener. Blocking.
     * @return the set of registered service addresses
     */
    public Set<String> registeredServiceAddresses() {
        IgniteCache<IgniteRegistrationInfo, Boolean> cache = ignite.cache(VERTX_SUBSCRIPTION_CACHE);
        if(cache == null){
            throw new IllegalStateException("The vertx subscription cache is not available");
        }
        Set<String> addresses = new HashSet<>();
        for(Cache.Entry<IgniteRegistrationInfo, Boolean> entry : cache){
            String address = entry.getKey().address();
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
     * Per-address sink plus the subscriber count used to remove idle entries. Emission is
     * synchronized so a seed racing an update event cannot overwrite the newer status with a stale one.
     */
    private static class AddressMonitor {

        final Sinks.Many<ListenerStatus> sink = Sinks.many().replay().latest();
        int subscribers; // mutated only inside monitors.compute* blocks for this address
        private boolean emitted;

        synchronized void emit(ListenerStatus status) {
            emitted = true;
            tryEmit(status);
        }

        synchronized void seed(ListenerStatus status) {
            if(!emitted){
                emitted = true;
                tryEmit(status);
            }
        }

        synchronized void fail(Throwable throwable) {
            sink.tryEmitError(throwable);
        }

        private void tryEmit(ListenerStatus status) {
            Sinks.EmitResult result = sink.tryEmitNext(status);
            if(result.isFailure()){
                log.warn("Failed to emit ListenerStatus {}: {}", status, result);
            }
        }
    }

    /**
     * Sink for service-address registration changes plus the subscriber count used to drop the monitor when
     * idle. Changes are dropped when no subscriber is attached — a subscriber establishes its baseline with
     * {@link #registeredServiceAddresses} anyway.
     */
    private static class ServiceMonitor {

        final Sinks.Many<ListenerChange> sink = Sinks.many().multicast().directBestEffort();
        int subscribers; // mutated only while serviceMonitorLock is held

        void emit(ListenerChange change) {
            Sinks.EmitResult result = sink.tryEmitNext(change);
            if(result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER){
                log.warn("Failed to emit ListenerChange {}: {}", change, result);
            }
        }

        void fail(Throwable throwable) {
            sink.tryEmitError(throwable);
        }
    }
}
