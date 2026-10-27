package org.kinotic.client.internal.api;

/**
 * A request watched by a {@link ConnectionLivenessWatcher}: the connection that acknowledged it, and the callback
 * run once that connection has ended.
 *
 * Created by Navíd Mitchell 🤪 on 10/27/26.
 */
record ConnectionWatch(String connectionId, Runnable onConnectionLost) {}
