package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.MicroserviceArtifact;
import org.kinotic.management.api.model.ProjectArtifacts;
import org.kinotic.management.api.model.UiArtifact;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.ProjectArtifactService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProjectArtifactService implements ProjectArtifactService {

    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final SecurityContext securityContext;

    @Override
    public Future<Void> recordArtifacts(String projectId, String commitSha, ProjectArtifacts artifacts) {
        Validate.notBlank(projectId, "projectId is required");
        Validate.notBlank(commitSha, "commitSha is required");
        Validate.notNull(artifacts, "artifacts is required");
        validate(artifacts);
        // A project's machines are ORGANIZATION scope, so an application participant is a
        // caller that can never be the sync workload
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return projectDeploymentRepository.findById(projectId, participant.getOrganizationId())
                .compose(deployment -> {
                    // Only the sync workload the deployment issued credentials to may report for
                    // the project; an org member or another project's machine gets the same
                    // answer as a project that does not exist
                    if (deployment == null || !participant.getId().equals(deployment.getSyncMachineIdentityId())) {
                        log.error("Participant {} may not record artifacts for project {}",
                                  participant.getId(), projectId);
                        throw new AuthorizationException("Access denied");
                    }
                    deployment.setArtifacts(artifacts);
                    deployment.setArtifactsCommitSha(commitSha);
                    deployment.setUpdated(new Date());
                    return projectDeploymentRepository.save(deployment, deployment.getOrganizationId());
                })
                .mapEmpty();
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
