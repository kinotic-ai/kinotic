package org.kinotic.client.api;

/**
 * The state of a {@link KinoticConnection}.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public enum ConnectionState {

    /**
     * The gateway has accepted the connection; calls are sent as they are made.
     */
    CONNECTED,

    /**
     * The connection was lost and is being re-established. Every call that was pending failed, and a call made
     * now fails until the gateway accepts the connection again.
     */
    RECONNECTING,

    /**
     * The connection is closed for good, by {@link KinoticConnection#close()} or because connecting failed as
     * often as its {@link ReconnectOptions} allow.
     */
    CLOSED

}
