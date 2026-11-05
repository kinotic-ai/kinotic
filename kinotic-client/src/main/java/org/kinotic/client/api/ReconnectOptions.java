package org.kinotic.client.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

/**
 * How a {@link KinoticConnection} retries connecting: on the initial connect until the gateway accepts it, and
 * after a connection is lost until it is re-established. The delay between attempts doubles from
 * {@link #getInitialDelay()} up to {@link #getMaxDelay()} and resets once a connection is established; after a
 * loss, a random jitter up to {@link #getMaxJitter()} is added so clients do not all return at once. A gateway
 * that cannot be reached, or answers the WebSocket upgrade with a 5xx status, as it does with a 503 while its own
 * security service cannot answer, is retried; a 4xx answer ends the connect, or the connection, for good, since no
 * retry changes it.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ReconnectOptions {

    /**
     * The delay before the first retry, doubled on every further failed attempt.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration initialDelay = Duration.ofSeconds(2);

    /**
     * The longest delay between two attempts.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration maxDelay = Duration.ofMinutes(2);

    /**
     * The most jitter added to the delay of an attempt that follows a lost connection.
     */
    @NotNull
    @DurationMin(millis = 0)
    private Duration maxJitter = Duration.ofSeconds(5);

    /**
     * How long each step of an attempt may take before the attempt counts as failed: the TCP connection, the
     * WebSocket handshake, then the gateway's answer to the CONNECT frame. It also bounds the wait for the gateway's
     * DISCONNECT receipt on close.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * The number of consecutive failed attempts that ends the connection for good, failing the initial connect or
     * closing an established connection; {@code 0} retries forever.
     */
    @Min(0)
    private int maxAttempts = 0;

    /**
     * A copy of the given options.
     * @param other the options to copy
     */
    public ReconnectOptions(ReconnectOptions other) {
        this.initialDelay = other.initialDelay;
        this.maxDelay = other.maxDelay;
        this.maxJitter = other.maxJitter;
        this.connectionTimeout = other.connectionTimeout;
        this.maxAttempts = other.maxAttempts;
    }

}
