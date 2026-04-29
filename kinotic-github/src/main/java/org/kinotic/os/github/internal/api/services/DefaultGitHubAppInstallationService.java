package org.kinotic.os.github.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.AuthScopeType;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.github.api.model.InstallStartResponse;
import org.kinotic.os.github.api.services.GitHubAppInstallationService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Default impl: CRUD over the {@code kinotic_github_app_installation} index plus the
 * {@code startInstall()} entry point and the {@link #findForCurrentOrg()} helper that
 * the SPA polls to render the org-settings "GitHub linked / not linked" card.
 * Inherits org-scope filtering from {@link AbstractCrudService} so callers cannot
 * read installations belonging to other orgs.
 */
@Slf4j
@Component
public class DefaultGitHubAppInstallationService
        extends AbstractCrudService<GitHubAppInstallation>
        implements GitHubAppInstallationService {

    private static final String INDEX = "kinotic_github_app_installation";

    private final KinoticGithubProperties properties;
    private final GitHubInstallStateStore stateStore;

    public DefaultGitHubAppInstallationService(CrudServiceTemplate crudServiceTemplate,
                                               ElasticsearchAsyncClient esAsyncClient,
                                               SecurityContext securityContext,
                                               KinoticGithubProperties properties,
                                               GitHubInstallStateStore stateStore) {
        super(INDEX, GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.properties = properties;
        this.stateStore = stateStore;
    }

    @Override
    public CompletableFuture<InstallStartResponse> startInstall() {
        String orgId = requireOrgId();
        String slug = properties.getGithub().getAppSlug();
        if (slug == null || slug.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("kinotic.github.appSlug is not configured"));
        }
        String state = stateStore.stage(orgId);
        String url = "https://github.com/apps/" + slug + "/installations/new?state=" + state;
        return CompletableFuture.completedFuture(new InstallStartResponse(url));
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findForCurrentOrg() {
        String orgId = requireOrgId();
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("organizationId").value(orgId)))));
        return crudServiceTemplate.search(INDEX,
                org.kinotic.core.api.crud.Pageable.ofSize(1),
                GitHubAppInstallation.class,
                b -> b.routing(orgId).query(q))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    private String requireOrgId() {
        Participant participant = securityContext.currentParticipant();
        if (participant == null
                || !AuthScopeType.ORGANIZATION.name().equals(participant.getAuthScopeType())) {
            throw new AuthorizationException("ORGANIZATION-scoped session required");
        }
        return participant.getAuthScopeId();
    }
}
