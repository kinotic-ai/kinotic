package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.management.api.model.deployment.ProjectSbom;
import org.kinotic.management.api.repositories.ProjectSbomRepository;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the members of an organization read of a project's SBOM through {@link ProjectService}: the
 * record a deployment saved for one of their projects, and nothing of another organization's.
 */
@SpringBootTest
public class ProjectSbomTests extends KinoticTestBase {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSbomRepository projectSboms;

    private final List<String> projectIds = new ArrayList<>();

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : projectIds) {
            await(projectSboms.deleteByIdSync(id, TEST_ORG_ID));
        }
        projectIds.clear();
    }

    @Test
    public void aMemberReadsTheSbomOfItsOrganizationsProject() throws Exception {
        String projectId = projectWithSbom("sbom-read");

        ProjectSbom sbom = await(runAsOrganization(() -> projectService.findSbom(projectId)));

        assertNotNull(sbom);
        assertEquals(projectId, sbom.getId());
        assertEquals(COMMIT, sbom.getCommitSha());
        assertEquals("hash-1", sbom.getDependencyHash());
    }

    @Test
    public void anotherOrganizationsMemberReadsNoSbom() throws Exception {
        String projectId = projectWithSbom("sbom-other-organization");
        DefaultOrganizationParticipant outsider = new DefaultOrganizationParticipant("sbom-outsider",
                                                                                     "sbom-other-org",
                                                                                     Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                            ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                                     List.of());

        assertNull(await(runAs(outsider, () -> projectService.findSbom(projectId))));
        assertNull(await(runAs(outsider, () -> projectService.findSbomDocumentUrl(projectId))));
    }

    @Test
    public void aProjectWithoutAnSbomHasNoDocument() throws Exception {
        assertNull(await(runAsOrganization(() -> projectService.findSbom("sbom-none"))));
        assertNull(await(runAsOrganization(() -> projectService.findSbomDocumentUrl("sbom-none"))));
    }

    /**
     * A project of the test organization with the SBOM a deployment saves once its SBOM workload
     * succeeded.
     */
    private String projectWithSbom(String projectId) throws Exception {
        projectIds.add(projectId);
        await(projectSboms.saveSync(new ProjectSbom().setId(projectId)
                                                     .setOrganizationId(TEST_ORG_ID)
                                                     .setApplicationId(TEST_APP_ID)
                                                     .setCommitSha(COMMIT)
                                                     .setDependencyHash("hash-1")
                                                     .setGenerated(new Date()),
                                    TEST_ORG_ID));
        return projectId;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
