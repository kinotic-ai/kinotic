package org.kinotic.core.api.event;

import java.util.Locale;

/**
 * Controls how a gateway session is kept alive.
 */
public enum SessionKeepAliveMode {

    /**
     * The session is removed when the transport connection closes.
     */
    NONE,

    /**
     * Session expiration is extended when gateway activity occurs.
     */
    ACTIVITY,

    /**
     * Session expiration is extended while the transport connection remains active.
     */
    CONNECTION;

    public static SessionKeepAliveMode fromHeader(String value) {
        return value != null ? valueOf(value.toUpperCase(Locale.ROOT)) : ACTIVITY;
    }

}
