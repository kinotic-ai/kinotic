package org.kinotic.system.internal.api.services;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.management.api.model.GitHubProjectEvent;
import org.kinotic.management.api.model.GitHubWebhookEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Event handling over scripted deliveries: only pushes to the default branch deploy.
 */
public class ProjectDeployOrchestratorTest {

    private static final String SHA_1 = "1".repeat(40);
    private static final String SHA_2 = "2".repeat(40);

    private RecordingProjectDeployOrchestrator deploys;

    @BeforeEach
    void setUp() {
        deploys = new RecordingProjectDeployOrchestrator();
    }

    @Test
    public void onlyPushesToTheDefaultBranchDeploy() {
        deploys.onEvent(event("proj-1", push(SHA_1, "refs/heads/feature", "main", false)));
        deploys.onEvent(event("proj-1", webhook("pull_request", new JsonObject())));
        deploys.onEvent(event("proj-1", push("0".repeat(40), "refs/heads/main", "main", false)));
        deploys.onEvent(event("proj-1", push(SHA_1, "refs/heads/main", "main", true)));
        deploys.onEvent(event("proj-1", push(SHA_2, "refs/heads/main", "main", false)));

        assertEquals(List.of(SHA_2), deploys.deployedShas);
    }

    private static GitHubProjectEvent event(String projectId, GitHubWebhookEvent webhook) {
        return new GitHubProjectEvent("org-1", projectId, webhook);
    }

    private static GitHubWebhookEvent push(String after, String ref, String defaultBranch, boolean deleted) {
        JsonObject payload = new JsonObject()
                .put("ref", ref)
                .put("after", after)
                .put("deleted", deleted)
                .put("repository", new JsonObject().put("default_branch", defaultBranch));
        return webhook("push", payload);
    }

    private static GitHubWebhookEvent webhook(String eventType, JsonObject payload) {
        return new GitHubWebhookEvent(eventType, "delivery-1", 1L, "acme/proj", payload);
    }

}
