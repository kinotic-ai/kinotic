package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.ClusterEventHandler;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * The connections open on this node, by the identity they are authenticated as, so a revocation delivered to the
 * cluster ends the ones of the identity it names.
 */
@Slf4j
@Component
public class ConnectionRegistry implements ClusterEventHandler<ConnectionRevocation> {

    private final Map<String, Set<Consumer<Throwable>>> connections = new ConcurrentHashMap<>();

    /**
     * Registers a connection under the identity it is authenticated as, to be ended when a revocation names it.
     *
     * @param identityId the identity
     * @param close      ends the connection with the cause given
     * @return removes the registration, once the connection has ended
     */
    public Runnable register(String identityId, Consumer<Throwable> close) {
        String key = identityId;
        // added under the key's lock, so a revoke or an unregister detaching the set cannot slip in between
        // finding the set and adding to it and leave the closer in a set nothing reads
        connections.compute(key, (k, closers) -> {
            Set<Consumer<Throwable>> registered = closers != null ? closers : new CopyOnWriteArraySet<>();
            registered.add(close);
            return registered;
        });
        return () -> connections.computeIfPresent(key, (k, closers) -> {
            closers.remove(close);
            return closers.isEmpty() ? null : closers;
        });
    }

    /** Ends every connection the revocation names; one that names none is handled as nothing. */
    @Override
    public void handle(ConnectionRevocation revocation) {
        Set<Consumer<Throwable>> closers = connections.remove(revocation.getIdentityId());
        if (closers != null) {
            log.debug("Ending {} connection(s) of identity {}", closers.size(), revocation.getIdentityId());
            closers.forEach(close -> close.accept(new AuthenticationException("The connection's authorization was revoked")));
        }
    }

}
