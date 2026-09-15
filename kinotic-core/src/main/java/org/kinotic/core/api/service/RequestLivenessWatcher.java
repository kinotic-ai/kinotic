package org.kinotic.core.api.service;

/**
 * Runs a callback when the node serving a request leaves the cluster before the request finished. A
 * caller starts watching a request once a node's acknowledgement names that node, and stops watching it
 * once the reply arrives; a request still watched when its node leaves has its callback run once, on the
 * Vert.x context the watch was started from.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
public interface RequestLivenessWatcher {

    /**
     * Starts watching a request's node.
     * @param requestKey identifies the request, unique across every caller of this watcher
     * @param nodeId the node the request's acknowledgement named
     * @param onNodeLeft run once if the node leaves the cluster before the request is unwatched
     */
    void watch(String requestKey, String nodeId, Runnable onNodeLeft);

    /**
     * Stops watching a request. Unwatching a request that is not watched, or whose node already left, has no
     * effect.
     * @param requestKey the key the request was watched under
     */
    void unwatch(String requestKey);

    /**
     * @return the number of requests currently watched
     */
    int watchedCount();
}
