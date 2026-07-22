package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;

import java.util.LinkedList;
import java.util.Set;

/**
 * Authorizes STOMP sends and subscriptions for a connected participant against the zones the
 * participant may address. Zones come from the CRI itself, so an un-zoned address is only ever
 * reachable by a participant that may send to any zone.
 */
@Slf4j
public class StompAuthorizer {

    private static final int MAX_TEMPORARY_GRANTS = 1000;

    private final boolean sendAnyZone;
    private final Set<String> sendZones;
    private final Set<String> hostZones;
    private final String replyToId;
    private final LinkedList<String> temporarySendGrants = new LinkedList<>();

    public StompAuthorizer(boolean sendAnyZone,
                           Set<String> sendZones,
                           Set<String> hostZones,
                           String replyToId) {
        this.sendAnyZone = sendAnyZone;
        this.sendZones = sendZones;
        this.hostZones = hostZones;
        this.replyToId = replyToId;
    }

    /**
     * Grants one send to the given destination, consumed by the next matching {@link #sendAllowed(CRI)}.
     * @param rawCri the destination to allow, matched exactly after normalization
     */
    public void addTemporarySendAllowed(String rawCri) {
        if (temporarySendGrants.size() == MAX_TEMPORARY_GRANTS) {
            temporarySendGrants.removeFirst();
            log.warn("Reached max temporary grants some messages may be dropped");
        }
        // normalizing through CRI folds the identity, so the grant compares equal to the folded
        // raw() of the send it authorizes
        temporarySendGrants.add(CRI.create(rawCri).raw());
    }

    public boolean sendAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        boolean ret;
        if (temporarySendGrants.remove(cri.raw())) {
            ret = true;
        } else if (isRoutableScheme(cri.scheme())) {
            ret = sendAnyZone || zoneAllowed(cri.zone(), sendZones);
        } else {
            ret = false;
        }
        return ret;
    }

    public boolean subscribeAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        boolean ret;
        if (EventConstants.REPLY_DESTINATION_SCHEME.equals(cri.scheme())) {
            // a reply destination is scoped to the connection: the scope carries the replyToId
            // followed by ':' and the subscription discriminator
            String scope = cri.scope();
            ret = scope != null && scope.startsWith(replyToId + ":");
        } else if (isRoutableScheme(cri.scheme())) {
            ret = zoneAllowed(cri.zone(), hostZones);
        } else {
            ret = false;
        }
        return ret;
    }

    private static boolean isRoutableScheme(String scheme) {
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(scheme)
                || EventConstants.STREAM_DESTINATION_SCHEME.equals(scheme);
    }

    // A zone is allowed when it is an allowed zone or a sub-zone of one; the dot boundary keeps
    // 'app.acme-org.orders-app-2' from matching 'app.acme-org.orders-app'
    private static boolean zoneAllowed(String zone, Set<String> allowedZones) {
        boolean ret = false;
        if (zone != null) {
            for (String allowed : allowedZones) {
                if (zone.equals(allowed) || zone.startsWith(allowed + ".")) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

}
