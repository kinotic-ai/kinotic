package org.kinotic.management.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.deployment.MicroserviceArtifact;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.UiArtifact;
import org.kinotic.management.api.repositories.ProjectDependenciesRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.deployment.ProjectArtifactService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DefaultProjectArtifactService implements ProjectArtifactService {

    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectDependenciesRepository projectDependenciesRepository;
    private final SecurityContext securityContext;

    @Override
    public Future<Void> recordArtifacts(String projectId, ProjectArtifacts artifacts) {
        Validate.notBlank(projectId, "projectId is required");
        Validate.notNull(artifacts, "artifacts is required");
        Validate.notBlank(artifacts.commitSha(), "artifacts.commitSha is required");
        validate(artifacts);
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return loadOwned(projectId, participant)
                .compose(deployment -> {
                    // the SBOM lists the dependencies it was generated from, so a report of other
                    // dependencies drops it and the deployment generates it again
                    boolean sameDependencies = deployment.getArtifacts() != null
                            && Objects.equals(deployment.getArtifacts().dependencyHash(), artifacts.dependencyHash());
                    return projectDeploymentRepository.recordArtifacts(projectId, participant.getOrganizationId(), artifacts,
                                                                       sameDependencies && deployment.isSbomGenerated());
                });
    }

    @Override
    public Future<Void> recordSbom(String projectId, String dependencyHash, ProjectDependencies dependencies) {
        Validate.notBlank(projectId, "projectId is required");
        Validate.notBlank(dependencyHash, "dependencyHash is required");
        Validate.notNull(dependencies, "dependencies is required");
        validate(dependencies);
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        String organizationId = participant.getOrganizationId();
        return loadOwned(projectId, participant)
                .compose(deployment -> {
                    // the SBOM is recorded as the one of the dependencies the artifacts list, so it
                    // must have been generated from those
                    Validate.isTrue(deployment.getArtifacts() != null && dependencyHash.equals(deployment.getArtifacts().dependencyHash()),
                                    "Dependency hash %s is not the one the sync workload of project %s last reported", dependencyHash, projectId);
                    dependencies.setId(projectId)
                                .setOrganizationId(organizationId)
                                .setApplicationId(deployment.getApplicationId());
                    return projectDependenciesRepository.save(dependencies, organizationId)
                            .compose(saved -> projectDeploymentRepository.recordSbomGenerated(projectId, organizationId));
                });
    }

    /** Loads the deployment of a project of the participant's organization; another organization's is indistinguishable from none. */
    private Future<ProjectDeployment> loadOwned(String projectId, OrganizationParticipant participant) {
        return projectDeploymentRepository.findById(projectId, participant.getOrganizationId())
                .map(deployment -> DomainUtil.requireOwned(deployment, participant.getOrganizationId(), "Project deployment not found."));
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

    // Readers resolve every position through packages, and look a package up by its URL, so a tree
    // whose positions run past its packages or that lists a package twice is refused
    private static void validate(ProjectDependencies dependencies) {
        List<String> packages = dependencies.getPackages();
        Validate.notNull(packages, "dependencies.packages is required");
        Validate.noNullElements(packages, "dependencies.packages holds null at position %d");
        Validate.isTrue(new HashSet<>(packages).size() == packages.size(), "dependencies.packages lists a package twice");
        int count = packages.size();
        requirePositions(dependencies.getDirect(), count, "direct");
        requirePositions(dependencies.getDevelopment(), count, "development");
        requirePositions(dependencies.getOptional(), count, "optional");
        Validate.notNull(dependencies.getEdges(), "dependencies.edges is required");
        for (int[] edge : dependencies.getEdges()) {
            Validate.isTrue(edge != null && edge.length == 2 && isPosition(edge[0], count) && isPosition(edge[1], count),
                            "dependencies.edges holds an edge that is not two positions in packages");
        }
    }

    private static void requirePositions(List<Integer> positions, int count, String name) {
        Validate.notNull(positions, "dependencies.%s is required", name);
        for (Integer position : positions) {
            Validate.isTrue(position != null && isPosition(position, count), "dependencies.%s holds %s, which is not a position in packages", name, position);
        }
    }

    private static boolean isPosition(int position, int count) {
        return position >= 0 && position < count;
    }

}
