package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.kinotic.core.api.security.ConnectionRevocationType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * The connections open on this node, by the identity they are authenticated as and by the browser login that
 * established them, so a revocation published to the cluster ends the ones it names.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionRegistry {

    private final Vertx vertx;
    private final Map<String, Set<Consumer<Throwable>>> connections = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        vertx.eventBus().<JsonObject>consumer(ConnectionRevocation.ADDRESS, message -> revoke(ConnectionRevocation.fromJson(message.body())));
    }

    /**
     * Registers a connection under the identity or login named, to be ended when a revocation names it.
     *
     * @param type  what {@code id} names
     * @param id    the identity or login
     * @param close ends the connection with the cause given
     * @return removes the registration, once the connection has ended
     */
    public Runnable register(ConnectionRevocationType type, String id, Consumer<Throwable> close) {
        String key = key(type, id);
        connections.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(close);
        return () -> connections.computeIfPresent(key, (k, closers) -> {
            closers.remove(close);
            return closers.isEmpty() ? null : closers;
        });
    }

    private void revoke(ConnectionRevocation revocation) {
        Set<Consumer<Throwable>> closers = connections.remove(key(revocation.getType(), revocation.getId()));
        if (closers != null) {
            log.debug("Ending {} connection(s) of {} {}", closers.size(), revocation.getType(), revocation.getId());
            closers.forEach(close -> close.accept(new AuthenticationException("The connection's authorization was revoked")));
        }
    }

    private static String key(ConnectionRevocationType type, String id) {
        return type + ":" + id;
    }

}
