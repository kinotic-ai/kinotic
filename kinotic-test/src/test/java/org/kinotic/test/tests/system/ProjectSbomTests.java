package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.MicroserviceArtifact;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.ProjectSbom;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.deployment.ProjectArtifactService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record a project's SBOM workload leaves behind, and what the organization's members read of
 * it: only the machine the project's sync workload runs as records an SBOM, and only for the
 * commit that workload reported artifacts for.
 */
@SpringBootTest
public class ProjectSbomTests extends KinoticTestBase {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SYNC_MACHINE_ID = "sbom-sync-machine";

    @Autowired
    private ProjectArtifactService projectArtifactService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectDeploymentRepository projectDeployments;

    private final List<String> projectIds = new ArrayList<>();

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : projectIds) {
            await(projectDeployments.deleteByIdSync(id, TEST_ORG_ID));
        }
        projectIds.clear();
    }

    @Test
    public void theSyncMachineRecordsTheSbomOfTheCommitItReportedArtifactsFor() throws Exception {
        String projectId = deployedProject("sbom-recorded");

        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, COMMIT, "hash-1", 42)));

        ProjectSbom sbom = await(runAsOrganization(() -> projectService.findDeployment(projectId))).getSbom();
        assertNotNull(sbom);
        assertEquals(COMMIT, sbom.commitSha());
        assertEquals("hash-1", sbom.dependencyHash());
        assertEquals(42, sbom.componentCount());
        assertNotNull(sbom.generated());
    }

    @Test
    public void aLaterSbomReplacesTheProjectsEarlierOne() throws Exception {
        String projectId = deployedProject("sbom-replaced");
        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, COMMIT, "hash-1", 42)));

        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, COMMIT, "hash-2", 43)));

        ProjectSbom sbom = await(runAsOrganization(() -> projectService.findDeployment(projectId))).getSbom();
        assertEquals("hash-2", sbom.dependencyHash());
        assertEquals(43, sbom.componentCount());
    }

    @Test
    public void anSbomOfACommitTheSyncDidNotReportIsRefused() throws Exception {
        String projectId = deployedProject("sbom-other-commit");

        Exception failure = assertThrows(Exception.class, () -> await(runAs(syncMachine(),
                () -> projectArtifactService.recordSbom(projectId, "f".repeat(40), "hash-1", 42))));

        assertTrue(failure.getMessage().contains("is not the one the sync workload"), failure.getMessage());
        assertNull(await(projectDeployments.findById(projectId, TEST_ORG_ID)).getSbom());
    }

    @Test
    public void onlyTheProjectsSyncMachineRecordsItsSbom() throws Exception {
        String projectId = deployedProject("sbom-member");

        assertThrows(Exception.class, () -> await(runAsOrganization(
                () -> projectArtifactService.recordSbom(projectId, COMMIT, "hash-1", 42))));

        assertNull(await(projectDeployments.findById(projectId, TEST_ORG_ID)).getSbom());
    }

    @Test
    public void aProjectWithoutAnSbomHasNoDocument() throws Exception {
        String projectId = deployedProject("sbom-none");

        assertNull(await(runAsOrganization(() -> projectService.findDeployment(projectId))).getSbom());
        assertNull(await(runAsOrganization(() -> projectService.findSbomDocumentUrl(projectId))));
    }

    /**
     * A project deployment whose sync workload runs as {@link #SYNC_MACHINE_ID} and reported the
     * artifacts of {@link #COMMIT}. No project record exists, so the reconcile master leaves it be.
     */
    private String deployedProject(String projectId) throws Exception {
        ProjectDeployment upsert = new ProjectDeployment().setId(projectId)
                                                          .setOrganizationId(TEST_ORG_ID)
                                                          .setApplicationId(TEST_APP_ID)
                                                          .setCreated(new Date())
                                                          .setUpdated(new Date());
        projectIds.add(projectId);
        await(projectDeployments.updateDesired(projectId, TEST_ORG_ID, new DeploymentState(DeploymentStatusType.RUNNING, COMMIT),
                                               upsert, "push of " + COMMIT));
        await(projectDeployments.recordSyncMachine(projectId, TEST_ORG_ID, SYNC_MACHINE_ID));
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID,
                                                 new ProjectArtifacts(COMMIT,
                                                                      List.of(new MicroserviceArtifact("api", "services/api", "index.ts")),
                                                                      List.of(),
                                                                      "hash-1")));
        return projectId;
    }

    private static OrganizationParticipant syncMachine() {
        return new DefaultOrganizationParticipant(SYNC_MACHINE_ID,
                                                  TEST_ORG_ID,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                         ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                  List.of());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
