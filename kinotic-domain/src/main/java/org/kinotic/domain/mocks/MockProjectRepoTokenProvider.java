package org.kinotic.domain.mocks;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.ProjectRepoToken;
import org.kinotic.domain.api.services.ProjectRepoTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link ProjectRepoTokenProvider} used when the GitHub module is disabled
 * ({@code kinotic.disableGithub=true}). Consumers can start without a GitHub App
 * configured; any actual token request fails, since there is no repository host to
 * mint one for.
 */
@Component
@ConditionalOnProperty(value = "kinotic.disableGithub", havingValue = "true")
public class MockProjectRepoTokenProvider implements ProjectRepoTokenProvider {

    @Override
    public Future<ProjectRepoToken> issueRepoToken(String organizationId, String projectId) {
        return Future.failedFuture(new IllegalStateException(
                "Cannot issue a repo token for project " + projectId + ": the GitHub module is disabled"));
    }
}
