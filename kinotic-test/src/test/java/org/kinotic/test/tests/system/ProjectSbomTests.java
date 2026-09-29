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
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.repositories.ProjectDependenciesRepository;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SBOM a project's deployment keeps, and what the organization's members read of it: only a
 * participant of the project's organization records an SBOM, only of the dependencies the sync
 * workload last reported, and a report of other dependencies drops it.
 */
@SpringBootTest
public class ProjectSbomTests extends KinoticTestBase {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String NEXT_COMMIT = "89abcdef0123456789abcdef0123456789abcdef";
    private static final String SYNC_MACHINE_ID = "sbom-sync-machine";

    @Autowired
    private ProjectArtifactService projectArtifactService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectDeploymentRepository projectDeployments;

    @Autowired
    private ProjectDependenciesRepository projectDependencies;

    private final List<String> projectIds = new ArrayList<>();

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : projectIds) {
            await(projectDependencies.deleteById(id, TEST_ORG_ID));
            await(projectDeployments.deleteByIdSync(id, TEST_ORG_ID));
        }
        projectIds.clear();
    }

    @Test
    public void theSyncMachineRecordsTheSbomOfTheDependenciesItReported() throws Exception {
        String projectId = deployedProject("sbom-recorded");

        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, "hash-1", tree())));

        assertTrue(await(runAsOrganization(() -> projectService.findDeployment(projectId))).isSbomGenerated());
        ProjectDependencies found = await(runAsOrganization(() -> projectService.findDependencies(projectId)));
        assertEquals(projectId, found.getId());
        assertEquals(TEST_APP_ID, found.getApplicationId());
        assertEquals(tree().getPackages(), found.getPackages());
        assertEquals(List.of(0, 2), found.getDirect());
        assertEquals(List.of(2), found.getDevelopment());
        assertEquals(1, found.getEdges().size());
        assertArrayEquals(new int[]{0, 1}, found.getEdges().getFirst());
    }

    @Test
    public void anSbomOfOtherDependenciesIsRefused() throws Exception {
        String projectId = deployedProject("sbom-other-dependencies");

        Exception failure = assertThrows(Exception.class, () -> await(runAs(syncMachine(),
                () -> projectArtifactService.recordSbom(projectId, "hash-2", tree()))));

        assertTrue(failure.getMessage().contains("is not the one the sync workload"), failure.getMessage());
        assertFalse(await(projectDeployments.findById(projectId, TEST_ORG_ID)).isSbomGenerated());
        assertNull(await(projectDependencies.findById(projectId, TEST_ORG_ID)));
    }

    @Test
    public void anSbomFromAnotherOrganizationIsRefused() throws Exception {
        String projectId = deployedProject("sbom-outsider");
        OrganizationParticipant outsider = new DefaultOrganizationParticipant("outsider",
                                                                              "outsider-org",
                                                                              Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                     ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                              List.of("ADMIN"));

        Exception failure = assertThrows(Exception.class, () -> await(runAs(outsider,
                () -> projectArtifactService.recordSbom(projectId, "hash-1", tree()))));

        assertTrue(failure.getMessage().contains("Project deployment not found"), failure.getMessage());
        assertFalse(await(projectDeployments.findById(projectId, TEST_ORG_ID)).isSbomGenerated());
        assertNull(await(projectDependencies.findById(projectId, TEST_ORG_ID)));
    }

    @Test
    public void aTreeWithAPositionOutsideItsPackagesIsRefused() throws Exception {
        String projectId = deployedProject("sbom-bad-edge");
        ProjectDependencies tree = tree().setEdges(List.of(new int[]{0, 3}));

        Exception failure = assertThrows(Exception.class, () -> await(runAs(syncMachine(),
                () -> projectArtifactService.recordSbom(projectId, "hash-1", tree))));

        assertTrue(failure.getMessage().contains("not two positions in packages"), failure.getMessage());
        assertNull(await(projectDependencies.findById(projectId, TEST_ORG_ID)));
    }

    @Test
    public void aTreeListingAPackageTwiceIsRefused() throws Exception {
        String projectId = deployedProject("sbom-duplicate");
        ProjectDependencies tree = tree().setPackages(List.of("pkg:npm/express@5.1.0", "pkg:npm/debug@4.4.1", "pkg:npm/express@5.1.0"));

        Exception failure = assertThrows(Exception.class, () -> await(runAs(syncMachine(),
                () -> projectArtifactService.recordSbom(projectId, "hash-1", tree))));

        assertTrue(failure.getMessage().contains("lists a package twice"), failure.getMessage());
        assertNull(await(projectDependencies.findById(projectId, TEST_ORG_ID)));
    }

    @Test
    public void aSyncOfTheSameDependenciesKeepsTheSbom() throws Exception {
        String projectId = deployedProject("sbom-kept");
        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, "hash-1", tree())));

        await(runAs(syncMachine(), () -> projectArtifactService.recordArtifacts(projectId, artifacts(NEXT_COMMIT, "hash-1"))));

        ProjectDeployment deployment = await(projectDeployments.findById(projectId, TEST_ORG_ID));
        assertEquals(NEXT_COMMIT, deployment.getArtifacts().commitSha());
        assertTrue(deployment.isSbomGenerated());
        assertEquals(tree().getPackages(), await(runAsOrganization(() -> projectService.findDependencies(projectId))).getPackages());
    }

    @Test
    public void aSyncOfOtherDependenciesDropsTheSbom() throws Exception {
        String projectId = deployedProject("sbom-dropped");
        await(runAs(syncMachine(), () -> projectArtifactService.recordSbom(projectId, "hash-1", tree())));

        await(runAs(syncMachine(), () -> projectArtifactService.recordArtifacts(projectId, artifacts(NEXT_COMMIT, "hash-2"))));

        assertFalse(await(projectDeployments.findById(projectId, TEST_ORG_ID)).isSbomGenerated());
        assertNull(await(runAsOrganization(() -> projectService.findDependencies(projectId))));
    }

    @Test
    public void aProjectWithoutAnSbomHasNoDependencies() throws Exception {
        String projectId = deployedProject("sbom-none");

        assertFalse(await(runAsOrganization(() -> projectService.findDeployment(projectId))).isSbomGenerated());
        assertNull(await(runAsOrganization(() -> projectService.findDependencies(projectId))));
    }

    /**
     * A project deployment whose sync workload reported the artifacts of {@link #COMMIT}, whose
     * dependencies hash to {@code hash-1}. No project record exists, so the reconcile master leaves
     * it be.
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
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID, artifacts(COMMIT, "hash-1"), false));
        return projectId;
    }

    /** express, which depends on debug, and typescript, which only development needs. */
    private static ProjectDependencies tree() {
        return new ProjectDependencies().setPackages(List.of("pkg:npm/express@5.1.0", "pkg:npm/debug@4.4.1", "pkg:npm/typescript@5.9.2"))
                                        .setDirect(List.of(0, 2))
                                        .setDevelopment(List.of(2))
                                        .setEdges(List.of(new int[]{0, 1}));
    }

    private static ProjectArtifacts artifacts(String commitSha, String dependencyHash) {
        return new ProjectArtifacts(commitSha, List.of(new MicroserviceArtifact("api", "services/api", "index.ts")), List.of(), dependencyHash);
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
