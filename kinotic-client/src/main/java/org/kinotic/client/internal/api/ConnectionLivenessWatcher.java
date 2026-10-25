package org.kinotic.client.internal.api;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.kinotic.core.api.service.RequestLivenessWatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches requests by the connection that acknowledged them. The gateway releases everything it holds for a
 * connection when the connection ends, so a request acknowledged on a connection that ends before its reply is lost
 * for good and every watch on that connection runs.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class ConnectionLivenessWatcher implements RequestLivenessWatcher {

    private final Map<String, Watch> watches = new ConcurrentHashMap<>();
    private volatile String liveConnectionId;

    /**
     * A connection was accepted: requests acknowledged on it are live until {@link #connectionEnded(String)}.
     * @param connectionId the id {@code sendWithAck} completes with for requests sent on the connection
     */
    public void connected(String connectionId) {
        liveConnectionId = connectionId;
    }

    /**
     * The connection ended: every request watched on it is lost.
     * @param connectionId the id of the connection that ended
     */
    public void connectionEnded(String connectionId) {
        if (connectionId.equals(liveConnectionId)) {
            liveConnectionId = null;
        }
        List<String> lost = new ArrayList<>();
        watches.forEach((requestKey, watch) -> {
            if (watch.connectionId().equals(connectionId)) {
                lost.add(requestKey);
            }
        });
        lost.forEach(this::connectionLost);
    }

    @Override
    public void watch(String requestKey, String nodeId, Runnable onNodeLeft) {
        watches.put(requestKey, new Watch(nodeId, onNodeLeft, Vertx.currentContext()));
        // the connection may have ended between the acknowledgement and this call
        if (!nodeId.equals(liveConnectionId)) {
            connectionLost(requestKey);
        }
    }

    @Override
    public void unwatch(String requestKey) {
        watches.remove(requestKey);
    }

    @Override
    public int watchedCount() {
        return watches.size();
    }

    // the callback runs on the request's own context, dispatched rather than inline, so a burst of lost requests
    // drains as separate tasks and never re-enters the caller that reported the loss
    private void connectionLost(String requestKey) {
        Watch watch = watches.remove(requestKey);
        if (watch != null) {
            if (watch.context() != null) {
                watch.context().runOnContext(v -> watch.onConnectionLost().run());
            } else {
                watch.onConnectionLost().run();
            }
        }
    }

    private record Watch(String connectionId, Runnable onConnectionLost, Context context) {}

}
