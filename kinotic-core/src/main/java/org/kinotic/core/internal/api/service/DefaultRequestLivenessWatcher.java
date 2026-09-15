package org.kinotic.core.internal.api.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches requests by node id against the cluster membership {@link EventBusService#monitorClusterNodes()}
 * reports. One membership subscription serves every request on the node; a membership change costs one
 * lookup per node that has requests watched on it.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRequestLivenessWatcher implements RequestLivenessWatcher {

    private final EventBusService eventBusService;
    private final Vertx vertx;
    private final OpenTelemetry openTelemetry;
    private final ConcurrentHashMap<String, Watch> watches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> requestKeysByNode = new ConcurrentHashMap<>();
    // null until the first membership snapshot arrives: a request watched before that is judged by the snapshot
    private volatile Set<String> members;
    private Disposable membership;
    private ObservableLongGauge watchedGauge;

    @PostConstruct
    void start() {
        // A count that climbs on a healthy node is the only visible sign of a callee that is up but wedged
        watchedGauge = openTelemetry.getMeter("kinotic.rpc.liveness")
                                    .gaugeBuilder("rpc.pending.requests")
                                    .setDescription("The number of requests in flight")
                                    .setUnit("requests")
                                    .ofLongs()
                                    .buildWithCallback(measurement -> measurement.record(watches.size()));
        membership = eventBusService.monitorClusterNodes()
                                    .subscribe(this::membershipChanged,
                                               throwable -> log.error("Cluster membership monitoring failed, pending requests can no longer be failed on node departure", throwable));
    }

    @PreDestroy
    void stop() {
        watchedGauge.close();
        membership.dispose();
    }

    @Override
    public void watch(String requestKey, String nodeId, Runnable onNodeLeft) {
        watches.put(requestKey, new Watch(nodeId, onNodeLeft, vertx.getOrCreateContext()));
        // the add runs under the key's lock, so a concurrent unwatch() emptying and dropping this node's set
        // cannot leave the key in a set that is no longer in the map
        requestKeysByNode.compute(nodeId, (_, requestKeys) -> {
            Set<String> ret = requestKeys != null ? requestKeys : ConcurrentHashMap.newKeySet();
            ret.add(requestKey);
            return ret;
        });
        // The node may have left between the acknowledgement and this call; the latest snapshot decides,
        // and membershipChanged catches a departure that lands while this method runs
        Set<String> current = members;
        if(current != null && !current.contains(nodeId)){
            nodeLeft(requestKey);
        }
    }

    @Override
    public void unwatch(String requestKey) {
        Watch watch = watches.remove(requestKey);
        if(watch != null){
            unindex(watch.nodeId(), requestKey);
        }
    }

    @Override
    public int watchedCount() {
        return watches.size();
    }

    private void membershipChanged(Set<String> nodes) {
        members = nodes;
        List<String> departed = new ArrayList<>();
        for(String nodeId : requestKeysByNode.keySet()){
            if(!nodes.contains(nodeId)){
                departed.add(nodeId);
            }
        }
        for(String nodeId : departed){
            Set<String> requestKeys = requestKeysByNode.remove(nodeId);
            if(requestKeys != null){
                requestKeys.forEach(this::nodeLeft);
            }
        }
    }

    // onNodeLeft is dispatched to the request's own context and never run on the membership delivery
    // context, so a death burst drains here as queue submissions rather than as caller code
    private void nodeLeft(String requestKey) {
        Watch watch = watches.remove(requestKey);
        if(watch != null){
            unindex(watch.nodeId(), requestKey);
            watch.context().runOnContext(_ -> watch.onNodeLeft().run());
        }
    }

    private void unindex(String nodeId, String requestKey) {
        requestKeysByNode.computeIfPresent(nodeId, (_, requestKeys) -> {
            requestKeys.remove(requestKey);
            return requestKeys.isEmpty() ? null : requestKeys;
        });
    }

    private record Watch(String nodeId, Runnable onNodeLeft, Context context) {}

}
