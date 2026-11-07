


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Provides functionality for non-persistent {@link Event}'s. Sends and publishes reach only the zones this
 * server's {@link ZonePartitioningService} reaches, and a listener is advertised to the cluster only in a zone it hosts.
 * The {@link RpcTransport} operations deliver within the cluster: {@link #sendWithAck(Event)} completes with the id
 * of the cluster node whose consumer took the event, and {@link #publish(Event)} reaches every node including this one.
 *
 * Created by navid on 10/30/19
 */
public interface EventBusService extends RpcTransport {

    /**
     * Send an {@link Event} to through the event bus.
     * @param event to send
     */
    void send(Event<byte[]> event);

    /**
     * Checks if any listeners are registered anywhere in the cluster for the {@link CRI#baseResource()} of the
     * given {@link CRI}, whether or not this server's {@link ZonePartitioningService} reaches it
     * @param cri to check if any listeners are active for
     * @return a {@link Future} that contains true if listeners are active false if not
     */
    Future<Boolean> isAnybodyListening(CRI cri);

    /**
     * Monitors the status of listeners anywhere in the cluster for the {@link CRI#baseResource()} of the given
     * {@link CRI}, whether or not this server's {@link ZonePartitioningService} reaches it
     * @param cri to check for registered listeners
     * @return a {@link Flux} that emits the current status on subscribe and every status transition after that
     */
    Flux<ListenerStatus> monitorListenerStatus(CRI cri);

    /**
     * Monitors the membership of the cluster. Node ids are the ones {@link #sendWithAck(Event)} completes
     * with, so a sender can tell whether the node that took its event is still part of the cluster.
     * @return a {@link Flux} that emits the current set of node ids on subscribe and the resulting set
     *         every time a node joins or leaves
     */
    Flux<Set<String>> monitorClusterNodes();

    /**
     * Monitors listener registration events across all service ({@code srv://}) addresses. A
     * {@link ServiceListenerChange} carries one address and its resulting status; a
     * {@link ServiceListenerContinuityLost} signals that changes may have been missed. The stream carries only
     * events; take a snapshot with {@link #activeServiceAddresses()} to establish a baseline, and rebuild it
     * whenever continuity is lost.
     * @return a {@link Flux} of {@link ServiceListenerEvent}s
     */
    Flux<ServiceListenerEvent> monitorServiceListenerEvents();

    /**
     * Snapshots every service ({@code srv://}) address that currently has a registered listener.
     * @return a {@link Future} containing the set of active service addresses
     */
    Future<Set<String>> activeServiceAddresses();

}
