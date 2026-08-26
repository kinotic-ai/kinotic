package org.kinotic.management.internal.api.services.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.model.security.SystemParticipant;
import org.kinotic.management.api.model.GitHubProjectEvent;
import org.kinotic.management.api.services.github.GitHubProjectEventService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Default {@link GitHubProjectEventService} that narrows the stream of every delivery this node
 * received to the organization the calling participant belongs to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubProjectEventService implements GitHubProjectEventService {

    private final GitHubWebhookProcessor webhookProcessor;
    private final SecurityContext securityContext;

    @Override
    public Flux<GitHubProjectEvent> events() {
        // Resolved before subscription: SecurityContext reads the calling Vert.x context, which is no
        // longer current once the returned Flux is subscribed
        Participant participant = securityContext.currentParticipant();
        Flux<GitHubProjectEvent> ret;
        if (participant instanceof SystemParticipant) {
            ret = webhookProcessor.events();
        } else if (participant instanceof OrganizationParticipant organizationParticipant) {
            String organizationId = organizationParticipant.getOrganizationId();
            ret = webhookProcessor.events()
                                     .filter(event -> organizationId.equals(event.getOrganizationId()));
        } else {
            // Name the participant server-side; surface only a generic message to the caller
            log.error("Participant {} may not subscribe to GitHub project events",
                      participant != null ? participant.getId() : null);
            throw new AuthorizationException("Access denied");
        }
        return ret;
    }
}
