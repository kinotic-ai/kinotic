package org.kinotic.management.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.deployment.MicroserviceArtifact;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.ProjectSbom;
import org.kinotic.management.api.model.deployment.UiArtifact;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.deployment.ProjectArtifactService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProjectArtifactService implements ProjectArtifactService {

    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final SecurityContext securityContext;

    @Override
    public Future<Void> recordArtifacts(String projectId, ProjectArtifacts artifacts) {
        Validate.notBlank(projectId, "projectId is required");
        Validate.notNull(artifacts, "artifacts is required");
        Validate.notBlank(artifacts.commitSha(), "artifacts.commitSha is required");
        validate(artifacts);
        // A project's machines are ORGANIZATION scope, so an application participant is a
        // caller that can never be the sync workload
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return findForSyncMachine(projectId, participant)
                .compose(deployment -> {
                    // the SBOM lists the dependencies it was generated from, so a report of other
                    // dependencies drops it and the deployment generates it again
                    boolean sameDependencies = deployment.getArtifacts() != null
                            && Objects.equals(deployment.getArtifacts().dependencyHash(), artifacts.dependencyHash());
                    return projectDeploymentRepository.recordArtifacts(projectId, participant.getOrganizationId(), artifacts,
                                                                       sameDependencies ? deployment.getSbom() : null);
                });
    }

    @Override
    public Future<Void> recordSbom(String projectId, String dependencyHash, int componentCount) {
        Validate.notBlank(projectId, "projectId is required");
        Validate.notBlank(dependencyHash, "dependencyHash is required");
        Validate.isTrue(componentCount >= 0, "componentCount cannot be negative");
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return findForSyncMachine(projectId, participant)
                .compose(deployment -> {
                    // the SBOM is recorded as the one of the dependencies the artifacts list, so it
                    // must have been generated from those
                    Validate.isTrue(deployment.getArtifacts() != null && dependencyHash.equals(deployment.getArtifacts().dependencyHash()),
                                    "Dependency hash %s is not the one the sync workload of project %s last reported", dependencyHash, projectId);
                    return projectDeploymentRepository.recordSbom(projectId, participant.getOrganizationId(),
                                                                  new ProjectSbom(componentCount, new Date()));
                });
    }

    /**
     * The project's deployment when the participant is the machine its sync workload runs as. Only
     * the workloads the deployment issued those credentials to may report for the project; an org
     * member or another project's machine gets the same answer as a project that does not exist.
     */
    private Future<ProjectDeployment> findForSyncMachine(String projectId, OrganizationParticipant participant) {
        return projectDeploymentRepository.findById(projectId, participant.getOrganizationId())
                .map(deployment -> {
                    if (deployment == null || !participant.getId().equals(deployment.getSyncMachineIdentityId())) {
                        log.error("Participant {} may not report for project {}", participant.getId(), projectId);
                        throw new AuthorizationException("Access denied");
                    }
                    return deployment;
                });
    }

    // A name becomes a workload name and a hostname label, and two artifacts of one kind with
    // one name would deploy as one, so a report breaking either rule is refused whatever the
    // workload found
    private static void validate(ProjectArtifacts artifacts) {
        Validate.notNull(artifacts.microservices(), "artifacts.microservices is required");
        Validate.notNull(artifacts.uis(), "artifacts.uis is required");
        Set<String> names = new HashSet<>();
        for (MicroserviceArtifact microservice : artifacts.microservices()) {
            requireArtifact(microservice.name(), microservice.dir(), names);
            Validate.notBlank(microservice.entry(), "Microservice %s has no entry", microservice.name());
        }
        names.clear();
        for (UiArtifact ui : artifacts.uis()) {
            requireArtifact(ui.name(), ui.dir(), names);
        }
    }

    private static void requireArtifact(String name, String dir, Set<String> namesOfKind) {
        ZoneUtil.validateLabel(name);
        Validate.notBlank(dir, "Artifact %s has no directory", name);
        Validate.isTrue(namesOfKind.add(name), "Two artifacts of one kind share the name '%s'", name);
    }

}
