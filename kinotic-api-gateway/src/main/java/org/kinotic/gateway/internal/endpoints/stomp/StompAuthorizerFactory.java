package org.kinotic.gateway.internal.endpoints.stomp;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
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
            if (!participant.getId().equals(ParticipantConstants.CLI_PARTICIPANT_ID)) {
                List<String> allowedSendPatterns = List.of(EventConstants.SERVICE_DESTINATION_SCHEME + "://*.**",
                                                           EventConstants.STREAM_DESTINATION_SCHEME + "://*.**");

                for (String path : allowedSendPatterns) {
                    sendPatterns.add(getPathPattern(path));
                }

                List<String> allowedSubscriptionPatterns = List.of(EventConstants.SERVICE_DESTINATION_SCHEME + "://*.**",
                                                                   EventConstants.STREAM_DESTINATION_SCHEME + "://*.**");

                for (String path : allowedSubscriptionPatterns) {
                    subscriptionPatterns.add(getPathPattern(path));
                }
            }

            subscriptionPatterns.add(getPathPattern(EventConstants.REPLY_DESTINATION_SCHEME + "://"
                                                            + replyToId
                                                            + ":*@*.**"));
        }
    }

}
