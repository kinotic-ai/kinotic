package org.kinotic.client.internal.api;

import io.vertx.core.Context;

/**
 * A request watched by a {@link ConnectionLivenessWatcher}: the connection that acknowledged it, the callback
 * run once that connection has ended, and the context the callback runs on, which the watch was started from.
 *
 * Created by Navíd Mitchell 🤪 on 10/27/26.
 */
record ConnectionWatch(String connectionId, Runnable onConnectionLost, Context context) {}
