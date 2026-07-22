package org.kinotic.gateway.internal.endpoints.stomp;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates STOMP authorizers using the gateway's current participant routing rules.
 *
 * A participant may only address zones its type allows: application participants reach the
 * {@code app-api} data plane and their own {@code app.<organizationId>.<applicationId>} zone and
 * may host services only inside their own zone, organization participants reach the {@code os-api}
 * management surface and host nothing, and system participants reach everything and host in the
 * platform zones.
 */
@Component
public class StompAuthorizerFactory {

    public StompAuthorizer create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");

        Participant participant = connectedInfo.getParticipant();
        StompAuthorizer ret;
        if (participant instanceof SystemParticipant) {

            ret = new StompAuthorizer(true,
                                      Set.of(),
                                      Set.of(DomainUtil.OS_API_ZONE, DomainUtil.APP_API_ZONE, DomainUtil.SYSTEM_ZONE),
                                      connectedInfo.getReplyToId());

        } else if (participant instanceof ApplicationParticipant applicationParticipant) {
            // appZone validates the ids, so an id that could shift the zone's label structure
            // fails the connection instead of widening access
            String appZone = appZone(applicationParticipant.getOrganizationId(),
                                     applicationParticipant.getApplicationId());
            ret = new StompAuthorizer(false,
                                      Set.of(DomainUtil.APP_API_ZONE, appZone),
                                      Set.of(appZone),
                                      connectedInfo.getReplyToId());

        } else if (participant instanceof OrganizationParticipant) {

            ret = new StompAuthorizer(false,
                                      Set.of(DomainUtil.OS_API_ZONE, DomainUtil.APP_API_ZONE),
                                      Set.of(),
                                      connectedInfo.getReplyToId());

        } else {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no zone routing rules exist for it");
        }
        return ret;
    }

    /**
     * Builds the zone that all of an application's services live in
     *
     * @param organizationId the id of the organization that owns the application
     * @param applicationId the id of the application
     * @return the application zone, app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    private String appZone(String organizationId, String applicationId) {
        // Each id must be a single dot-free label: a dot inside an id would shift the
        // app.<organizationId>.<applicationId> label structure, letting one (org, app) pair
        // produce the same zone as a different pair plus a sub zone
        ZoneUtil.validateLabel(organizationId);
        ZoneUtil.validateLabel(applicationId);
        return DomainUtil.APP_ZONE_PREFIX + "." + organizationId + "." + applicationId;
    }

}
