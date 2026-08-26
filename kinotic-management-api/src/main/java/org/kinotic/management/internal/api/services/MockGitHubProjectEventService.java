package org.kinotic.management.internal.api.services;

import org.kinotic.management.api.model.GitHubProjectEvent;
import org.kinotic.management.api.services.github.GitHubProjectEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Fallback {@link GitHubProjectEventService} used when the GitHub module is disabled
 * ({@code kinotic.managementApi.github.disable=true}): a stream that never emits, so subscribers such as
 * the deployment listener start normally and simply never see a push.
 */
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.github.disable", havingValue = "true")
public class MockGitHubProjectEventService implements GitHubProjectEventService {

    @Override
    public Flux<GitHubProjectEvent> events() {
        return Flux.never();
    }
}
