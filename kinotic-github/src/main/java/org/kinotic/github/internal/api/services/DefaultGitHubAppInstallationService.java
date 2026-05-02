package org.kinotic.github.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.services.GitHubAppInstallationService;
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
    private final GitHubInstallStateService stateService;

    public DefaultGitHubAppInstallationService(CrudServiceTemplate crudServiceTemplate,
                                               ElasticsearchAsyncClient esAsyncClient,
                                               SecurityContext securityContext,
                                               KinoticGithubProperties properties,
                                               GitHubInstallStateService stateService) {
        super(INDEX, GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.properties = properties;
        this.stateService = stateService;
    }

    @Override
    public CompletableFuture<String> startInstall() {
        String orgId = requireOrganizationId();
        String state = stateService.stage(orgId);
        return CompletableFuture.completedFuture(
                "https://github.com/apps/" + properties.getGithub().getAppSlug()
                        + "/installations/new?state=" + state);
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findForCurrentOrg() {
        String orgId = requireOrganizationId();
        Query q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("organizationId").value(orgId)))));
        return crudServiceTemplate.search(INDEX,
                Pageable.ofSize(1),
                GitHubAppInstallation.class,
                b -> b.routing(orgId).query(q))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        String orgId = getOrganizationIdIfEnforced();
        Query q;
        if (orgId != null) {
            q = Query.of(qb -> qb.bool(b -> b
                    .filter(f -> f.term(t -> t.field("organizationId").value(orgId)))
                    .filter(f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)))));
            return crudServiceTemplate.search(INDEX, Pageable.ofSize(1), GitHubAppInstallation.class,
                                              b -> b.routing(orgId).query(q))
                                      .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
        }
        q = Query.of(qb -> qb.bool(b -> b.filter(
                f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)))));
        return crudServiceTemplate.search(INDEX, Pageable.ofSize(1), GitHubAppInstallation.class,
                                          b -> b.query(q))
                                  .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }
}
