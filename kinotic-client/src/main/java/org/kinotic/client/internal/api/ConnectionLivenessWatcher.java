package org.kinotic.client.internal.api;

import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ConnectionLivenessWatcher implements RequestLivenessWatcher {

    private final Vertx vertx;
    private final Map<String, ConnectionWatch> watches = new ConcurrentHashMap<>();
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
        watches.put(requestKey, new ConnectionWatch(nodeId, onNodeLeft, vertx.getOrCreateContext()));
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

    // The callback is dispatched to the context the watch was started from rather than run inline: a burst of
    // lost requests drains as separate tasks, and watch() is called from inside the proxy's responseMap update,
    // which the callback's removal would re-enter
    private void connectionLost(String requestKey) {
        ConnectionWatch watch = watches.remove(requestKey);
        if (watch != null) {
            watch.context().runOnContext(v -> watch.onConnectionLost().run());
        }
    }

}
