package org.kinotic.client.api;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;

/**
 * How a {@link KinoticConnection} retries connecting: on the initial connect until the gateway accepts it, and
 * after a connection is lost until it is re-established. The delay between attempts doubles from
 * {@link #getInitialDelay()} up to {@link #getMaxDelay()} and resets once a connection is established; after a
 * loss, a random jitter up to {@link #getMaxJitter()} is added so clients do not all return at once. A gateway
 * that cannot be reached, or answers the WebSocket upgrade with a 5xx status, is retried; a 4xx answer ends the
 * connect, or the connection, for good, since no retry changes it, except that a reconnect retries a 401 within
 * the attempt budget, because a gateway whose security service is failing answers every connection with one.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@Accessors(chain = true)
public class ReconnectOptions {

    /**
     * The delay before the first retry, doubled on every further failed attempt.
     */
    private Duration initialDelay = Duration.ofSeconds(2);

    /**
     * The longest delay between two attempts.
     */
    private Duration maxDelay = Duration.ofMinutes(2);

    /**
     * The most jitter added to the delay of an attempt that follows a lost connection.
     */
    private Duration maxJitter = Duration.ofSeconds(5);

    /**
     * How long each step of an attempt may take before the attempt counts as failed: the TCP connection, the
     * WebSocket handshake, then the gateway's answer to the CONNECT frame. It also bounds the wait for the gateway's
     * DISCONNECT receipt on close.
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * The number of consecutive failed attempts that ends the connection for good, failing the initial connect or
     * closing an established connection; {@code 0} retries forever.
     */
    private int maxAttempts = 0;

}
