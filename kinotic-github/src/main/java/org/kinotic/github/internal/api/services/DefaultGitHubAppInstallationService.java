package org.kinotic.github.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallCompletion;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.InstallationDetails;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: CRUD over the {@code kinotic_github_app_installation} index plus the
 * three install-flow methods ({@link #startInstall(String, String)},
 * {@link #completeInstall(long, String)}, {@link #findForCurrentOrg()}). Inherits
 * org-scope filtering from {@link AbstractCrudService} so callers cannot read or
 * mutate installations belonging to other orgs.
 */
@Slf4j
@Component
public class DefaultGitHubAppInstallationService
        extends AbstractCrudService<GitHubAppInstallation>
        implements GitHubAppInstallationService {

    private static final String INDEX = "kinotic_github_app_installation";

    private final KinoticGithubProperties properties;
    private final GitHubInstallStateService stateService;
    private final GitHubApiClient apiClient;

    public DefaultGitHubAppInstallationService(CrudServiceTemplate crudServiceTemplate,
                                               ElasticsearchAsyncClient esAsyncClient,
                                               SecurityContext securityContext,
                                               KinoticGithubProperties properties,
                                               GitHubInstallStateService stateService,
                                               GitHubApiClient apiClient) {
        super(INDEX, GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.properties = properties;
        this.stateService = stateService;
        this.apiClient = apiClient;
    }

    @Override
    public CompletableFuture<String> startInstall(String returnTo) {
        String orgId = requireOrganizationId();
        StagedInstall staged = new StagedInstall()
                .setOrganizationId(orgId)
                .setReturnTo(returnTo);
        String state = stateService.stage(staged);
        return CompletableFuture.completedFuture(
                "https://github.com/apps/" + properties.getGithub().getAppSlug()
                        + "/installations/new?state=" + state);
    }

    @Override
    public CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state) {
        String callerOrgId = requireOrganizationId();
        StagedInstall staged = stateService.consume(state);
        if (staged == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Install state is missing, expired, or already used. Please re-link GitHub."));
        }
        if (!callerOrgId.equals(staged.getOrganizationId())) {
            return CompletableFuture.failedFuture(new AuthorizationException(
                    "Install state does not belong to the current organization."));
        }
        return apiClient.getInstallation(installationId)
                .compose(details -> persist(staged.getOrganizationId(), installationId, details))
                .map(installation -> new GitHubInstallCompletion()
                        .setInstallation(installation)
                        .setReturnTo(staged.getReturnTo()))
                .toCompletionStage().toCompletableFuture();
    }

    private Future<GitHubAppInstallation> persist(String orgId, long installationId, InstallationDetails details) {
        Date now = new Date();
        GitHubAppInstallation install = new GitHubAppInstallation()
                .setId(Long.toString(installationId))
                .setOrganizationId(orgId)
                .setGithubInstallationId(installationId)
                .setAccountLogin(details.accountLogin())
                .setAccountType(details.accountType())
                .setCreated(now)
                .setUpdated(now);
        return Future.fromCompletionStage(save(install));
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
