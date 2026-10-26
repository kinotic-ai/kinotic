package org.kinotic.management.internal.api.services.gitea;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.management.api.services.github.ProjectRepoCredentialService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Issues the credentials of projects' repositories on the Gitea server the system administers. Each repository is
 * read through one Gitea account that is a read-only collaborator of it alone, and written through another that may
 * write it alone, so a token reaches exactly one repository with exactly the access asked for. A token is deleted an
 * hour after it is issued.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.managementApi.repoHost", havingValue = "GITEA")
public class GiteaProjectRepoService implements ProjectRepoTokenProvider, ProjectRepoCredentialService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    // Gitea as a deployment's sync workload reaches it from its micro VM, through the host alias of a BOXLITE guest
    private static final String WORKLOAD_BASE_URL = "http://192.168.127.254:3001";
    // The repository accounts sign in only to have their tokens issued, by this server
    private static final String ACCOUNT_PASSWORD = "kinotic-gitea-repository";

    private final GiteaClient giteaClient;
    private final ProjectRepository projectRepository;
    private final SecurityContext securityContext;

    @Override
    public Future<ProjectRepoToken> issueRepoToken(String organizationId, String projectId) {
        return issue(organizationId, projectId, GiteaRepoAccess.READ, WORKLOAD_BASE_URL);
    }

    @Override
    public Future<ProjectRepoToken> issueWriteToken(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return issue(participant.getOrganizationId(), projectId, GiteaRepoAccess.WRITE, GiteaClient.WEB_BASE_URL);
    }

    // Callers pass the organization they act for, issueWriteToken its caller's, so authorization is the org-scoped
    // project lookup, which finds nothing for a project of another organization
    private Future<ProjectRepoToken> issue(String organizationId, String projectId, GiteaRepoAccess access, String cloneBaseUrl) {
        return projectRepository.findById(projectId, organizationId).compose(project -> {
            if (project == null || project.getRepoFullName() == null) {
                throw new IllegalStateException("Project " + projectId + " of organization " + organizationId
                                                + " has no repository provisioned");
            }
            String username = accountName(project, access);
            Instant issuedAt = Instant.now();
            return giteaClient.ensureCollaborator(project.getRepoFullName(), username, ACCOUNT_PASSWORD, access.getPermission())
                              .compose(v -> giteaClient.createToken(username, ACCOUNT_PASSWORD, access.getScope(), TOKEN_LIFETIME))
                              .map(token -> new ProjectRepoToken(token,
                                                                 issuedAt.plus(TOKEN_LIFETIME),
                                                                 cloneBaseUrl + "/" + project.getRepoFullName() + ".git",
                                                                 project.getRepoDefaultBranch()));
        });
    }

    // Gitea names an account in at most 40 characters, so the repository's account is named by a digest of the project
    private static String accountName(Project project, GiteaRepoAccess access) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                                         .digest((project.getOrganizationId() + "/" + project.getId()).getBytes(StandardCharsets.UTF_8));
            return access.getAccountPrefix() + HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

}
