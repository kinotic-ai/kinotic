package org.kinotic.test.support.kinotic;

import org.kinotic.core.api.annotations.KinoticConsumer;
import org.kinotic.core.api.event.EventDelivery;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the identities whose connections were revoked, as the API gateway, which this module runs without,
 * receives each revocation to end the identity's connections.
 */
@Component
public class ConnectionRevocationRecorder {

    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    @KinoticConsumer(delivery = EventDelivery.CONFIRMED)
    public void record(ConnectionRevocation revocation) {
        revoked.add(revocation.getIdentityId());
    }

    /**
     * @param identityId the identity to look for
     * @return true once a revocation naming the identity was delivered
     */
    public boolean isRevoked(String identityId) {
        return revoked.contains(identityId);
    }
}
