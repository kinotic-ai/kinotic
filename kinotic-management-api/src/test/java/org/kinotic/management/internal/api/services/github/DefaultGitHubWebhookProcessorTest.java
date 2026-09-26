package org.kinotic.management.internal.api.services.github;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.management.api.model.github.GitHubWebhookEvent;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.ProjectPushEvent;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.internal.api.repositories.GitHubAppInstallationRepository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deliveries over the real emitter: only a push to the default branch of a project's repository
 * reaches the fabric, as a project push.
 */
public class DefaultGitHubWebhookProcessorTest {

    private static final String SHA_1 = "1".repeat(40);
    private static final String SHA_2 = "2".repeat(40);

    private Vertx vertx;
    private DefaultGitHubWebhookProcessor processor;
    private final List<ProjectPushEvent> pushes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        Project project = new Project();
        project.setId("proj-1");
        project.setOrganizationId("org-1");
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByRepoFullName(anyString())).thenReturn(Future.succeededFuture(List.of(project)));
        processor = new DefaultGitHubWebhookProcessor(mock(GitHubAppInstallationRepository.class), projects, vertx);
        processor.init();
        processor.projectPushes().subscribe(pushes::add);
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    @Test
    public void onlyAPushToTheDefaultBranchReachesTheFabric() throws Exception {
        process(push(SHA_1, "refs/heads/feature", "main", false));
        process(webhook("pull_request", new JsonObject()));
        process(push("0".repeat(40), "refs/heads/main", "main", false));
        process(push(SHA_1, "refs/heads/main", "main", true));
        process(push(SHA_2, "refs/heads/main", "main", false));

        // the emission is posted to the delivery context after the delivery completes
        long deadline = System.currentTimeMillis() + 5_000;
        while (pushes.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, pushes.size());
        assertEquals("org-1", pushes.get(0).getOrganizationId());
        assertEquals("proj-1", pushes.get(0).getProjectId());
        assertEquals(SHA_2, pushes.get(0).getCommitSha());
    }

    private void process(GitHubWebhookEvent event) throws Exception {
        processor.process(event).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
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
