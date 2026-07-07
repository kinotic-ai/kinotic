package org.kinotic.gateway.internal.endpoints.stomp;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.security.PlatformZones;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates STOMP authorizers using the gateway's current participant routing rules.
 *
 * A participant may only address zones its type allows: application participants reach the
 * {@code api} zone and their own {@code app.<organizationId>.<applicationId>} zone and may host
 * services only inside their own zone, organization participants reach the {@code api} zone and
 * host nothing, and system participants reach everything and host in the platform zones.
 */
@Component
public class StompAuthorizerFactory {

    private final PathPatternParser parser;
    private final Map<String, PathPattern> pathPatternCache = new ConcurrentHashMap<>();

    public StompAuthorizerFactory() {
        this.parser = new PathPatternParser();
        this.parser.setPathOptions(PathContainer.Options.MESSAGE_ROUTE);
    }

    public StompAuthorizer create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");

        ParticipantPathPatterns participantPathPatterns = new ParticipantPathPatterns(connectedInfo.getParticipant(),
                                                                                      connectedInfo.getReplyToId());
        return new StompAuthorizer(parser.getPathOptions(),
                                   participantPathPatterns.sendPatterns,
                                   participantPathPatterns.subscriptionPatterns,
                                   this::getPathPattern);
    }

    private PathPattern getPathPattern(String pattern) {
        return pathPatternCache.computeIfAbsent(pattern, parser::parse);
    }

    private class ParticipantPathPatterns {
        private final List<PathPattern> sendPatterns = new LinkedList<>();
        private final List<PathPattern> subscriptionPatterns = new LinkedList<>();

        public ParticipantPathPatterns(Participant participant, String replyToId) {

            if (participant instanceof SystemParticipant) {
                addAllZones(sendPatterns);
                addZone(subscriptionPatterns, PlatformZones.API);
                addZone(subscriptionPatterns, PlatformZones.SYSTEM);

            } else if (participant instanceof ApplicationParticipant applicationParticipant) {
                // appZone validates the ids, so an id that could act as a wildcard or extra
                // label inside a pattern fails the connection instead of widening access
                String appZone = PlatformZones.appZone(applicationParticipant.getOrganizationId(),
                                                      applicationParticipant.getApplicationId());
                addZone(sendPatterns, PlatformZones.API);
                addZone(sendPatterns, appZone);
                addZone(subscriptionPatterns, appZone);

            } else if (participant instanceof OrganizationParticipant) {
                addZone(sendPatterns, PlatformZones.API);

            } else {
                throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                           + ", no zone routing rules exist for it");
            }

            subscriptionPatterns.add(getPathPattern(EventConstants.REPLY_DESTINATION_SCHEME + "://"
                                                            + replyToId
                                                            + ":*@*.**"));
        }

        private void addZone(List<PathPattern> target, String zone) {
            for (String scheme : List.of(EventConstants.SERVICE_DESTINATION_SCHEME,
                                         EventConstants.STREAM_DESTINATION_SCHEME)) {
                target.add(getPathPattern(scheme + "://" + zone + ".**"));
                // CRIs may carry a scope, e.g. srv://node1@system.kinotic-ai.vm-manager.VmManager
                target.add(getPathPattern(scheme + "://*@" + zone + ".**"));
            }
        }

        private void addAllZones(List<PathPattern> target) {
            for (String scheme : List.of(EventConstants.SERVICE_DESTINATION_SCHEME,
                                         EventConstants.STREAM_DESTINATION_SCHEME)) {
                target.add(getPathPattern(scheme + "://*.**"));
            }
        }
    }

}
