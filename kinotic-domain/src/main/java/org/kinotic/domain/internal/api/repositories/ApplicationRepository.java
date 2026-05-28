package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ApplicationRepository extends AbstractOrganizationScopedRepository<Application> {

    public ApplicationRepository(ElasticsearchAsyncClient esAsyncClient,
                                 CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_application", Application.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Looks up the {@link Application} whose source {@code id} field equals {@code appId},
     * searching across all orgs (non-routed). System-internal entry point for paths that
     * carry only the appId and need to derive its owning {@code organizationId} — primarily
     * the IAM participant builder when an APP-scope user authenticates.
     * <p>
     * Assumes appId is globally unique across organizations: the IamUser uniqueness rule
     * already treats {@code (authScopeId=appId, authScopeType=APPLICATION)} as a single
     * scope, so two orgs cannot host an app of the same id without violating IAM invariants.
     *
     * @param appId the application id (the source {@code id} field, not the composite ES {@code _id})
     * @return the matching Application, or {@code null} if none exists
     */
    public CompletableFuture<Application> findByAppId(String appId) {
        Validate.notBlank(appId, "appId cannot be blank");
        return findFirst(b -> b.query(termFilter("id", appId)));
    }
}
