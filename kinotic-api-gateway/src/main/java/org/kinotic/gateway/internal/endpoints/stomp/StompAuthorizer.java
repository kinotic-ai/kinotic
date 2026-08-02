package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.domain.api.security.ZoneRules;

import java.util.LinkedList;

/**
 * Authorizes STOMP sends and subscriptions for a connected participant: zone checks delegate to the
 * participant's {@link ZoneRules}, with temporary send grants and reply-destination scoping layered on top.
 */
@Slf4j
public class StompAuthorizer {

    private static final int MAX_TEMPORARY_GRANTS = 1000;

    private final ZoneRules zoneRules;
    private final String replyToId;
    private final LinkedList<String> temporarySendGrants = new LinkedList<>();

    public StompAuthorizer(ZoneRules zoneRules, String replyToId) {
        Validate.notNull(zoneRules, "zoneRules must not be null");
        Validate.notEmpty(replyToId, "replyToId must not be empty");
        this.zoneRules = zoneRules;
        this.replyToId = replyToId;
    }

    /**
     * Grants one send to the given destination, consumed by the next matching {@link #sendAllowed(CRI)}.
     * @param rawCri the destination to allow, matched exactly
     */
    public void addTemporarySendAllowed(String rawCri) {
        if (temporarySendGrants.size() == MAX_TEMPORARY_GRANTS) {
            temporarySendGrants.removeFirst();
            log.warn("Reached max temporary grants some messages may be dropped");
        }
        // round-tripping through CRI validates the grant and stores the same raw() form the
        // incoming send's CRI produces, so the two compare as exact strings
        temporarySendGrants.add(CRI.create(rawCri).raw());
    }

    public boolean sendAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        return temporarySendGrants.remove(cri.raw()) || zoneRules.sendAllowed(cri);
    }

    public boolean subscribeAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        boolean ret;
        if (EventConstants.REPLY_DESTINATION_SCHEME.equals(cri.scheme())) {
            // a reply destination is scoped to the connection: the scope carries the replyToId
            // followed by ':' and the subscription discriminator
            String scope = cri.scope();
            ret = scope != null && scope.startsWith(replyToId + ":");
        } else {
            ret = zoneRules.subscribeAllowed(cri);
        }
        return ret;
    }

}
