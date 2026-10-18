package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfigurationType;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Every {@link OidcConfiguration} in one index, the platform's managed registrations and the organizations' own
 * alike, told apart by the persisted {@code type} discriminator. The owner-specific finders filter on it, so a
 * lookup for one kind never answers with another.
 */
@Component
public class OidcConfigurationRepository extends AbstractRepository<OidcConfiguration> {

    private static final String TYPE_FIELD = "type";
    private static final String ORGANIZATION_ID_FIELD = "organizationId";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String PROVIDER_FIELD = "provider";
    private static final String ENABLED_FIELD = "enabled";

    public OidcConfigurationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, crudServiceTemplate);
    }

    /**
     * Every enabled registration offered to organization sign-up.
     */
    public Future<List<OrgSignupOidcConfiguration>> findEnabledOrgSignup() {
        return findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                termFilter(TYPE_FIELD, OidcConfigurationType.ORG_SIGNUP.name()),
                termFilter(ENABLED_FIELD, true))))
                .map(page -> page.map(OrgSignupOidcConfiguration.class::cast))
                .map(Page::getContent);
    }

    /**
     * The enabled org sign-up registration of the given provider kind, or {@code null} when there is none.
     */
    public Future<OrgSignupOidcConfiguration> findEnabledOrgSignupByProvider(OidcProviderKind provider) {
        return findFirst(b -> b.query(composeFilter(
                termFilter(TYPE_FIELD, OidcConfigurationType.ORG_SIGNUP.name()),
                termFilter(PROVIDER_FIELD, provider.key()),
                termFilter(ENABLED_FIELD, true))))
                .map(OrgSignupOidcConfiguration.class::cast);
    }

    /**
     * The org sign-up registration {@code id}, enabled or not, or {@code null} when no
     * {@link OrgSignupOidcConfiguration} has that id.
     */
    public Future<OrgSignupOidcConfiguration> findOrgSignupById(String id) {
        return findById(id).map(config -> config instanceof OrgSignupOidcConfiguration social ? social : null);
    }

    /**
     * The enabled social registrations of the given provider kinds, in the order of {@code providers}. A kind
     * the platform holds no enabled registration for is left out.
     */
    public Future<List<ApplicationSocialOidcConfiguration>> findEnabledSocialByProviders(Collection<OidcProviderKind> providers) {
        Future<List<ApplicationSocialOidcConfiguration>> ret;
        if (providers.isEmpty()) {
            ret = Future.succeededFuture(List.of());
        } else {
            List<FieldValue> keys = providers.stream().map(kind -> FieldValue.of(kind.key())).toList();
            ret = findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                    termFilter(TYPE_FIELD, OidcConfigurationType.APP_SOCIAL.name()),
                    termFilter(ENABLED_FIELD, true),
                    Query.of(q -> q.terms(t -> t.field(PROVIDER_FIELD).terms(v -> v.value(keys)))))))
                    .map(Page::getContent)
                    .map(found -> providers.stream()
                                           .map(kind -> found.stream()
                                                             .map(ApplicationSocialOidcConfiguration.class::cast)
                                                             .filter(config -> config.getProvider() == kind)
                                                             .findFirst()
                                                             .orElse(null))
                                           .filter(Objects::nonNull)
                                           .toList());
        }
        return ret;
    }

    /**
     * The configuration {@code id} that organization {@code orgId} owns, of any owned kind, enabled or not, or
     * {@code null} when that organization owns none by that id.
     */
    public Future<OwnedOidcConfiguration> findOwnedById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findById(id).map(config -> config instanceof OwnedOidcConfiguration owned
                                          && Objects.equals(orgId, owned.getOrganizationId()) ? owned : null);
    }

    /**
     * The enabled configurations organization {@code orgId} made for one of its sign-in scopes, oldest first: its
     * {@link org.kinotic.domain.api.model.security.OrganizationOidcConfiguration}s when {@code applicationId} is
     * null, the {@link org.kinotic.domain.api.model.security.ApplicationOidcConfiguration}s of that application
     * when {@code tenantId} is null, else the {@link org.kinotic.domain.api.model.security.TenantOidcConfiguration}s
     * of that tenant of the application.
     */
    public Future<List<OwnedOidcConfiguration>> findEnabledOwnedByScope(String orgId, String applicationId, String tenantId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                scopeFilter(orgId, applicationId, tenantId),
                termFilter(ENABLED_FIELD, true)))
                .sort(s -> s.field(f -> f.field("created"))))
                .map(page -> page.map(OwnedOidcConfiguration.class::cast))
                .map(Page::getContent);
    }

    // the owned kind of a scope is its discriminator, so a scope's configurations are one kind at one owner
    private Query scopeFilter(String orgId, String applicationId, String tenantId) {
        Query ret;
        if (applicationId == null) {
            ret = composeFilter(termFilter(TYPE_FIELD, OidcConfigurationType.ORGANIZATION.name()),
                                termFilter(ORGANIZATION_ID_FIELD, orgId));
        } else if (tenantId == null) {
            ret = composeFilter(termFilter(TYPE_FIELD, OidcConfigurationType.APPLICATION.name()),
                                termFilter(ORGANIZATION_ID_FIELD, orgId),
                                termFilter(APPLICATION_ID_FIELD, applicationId));
        } else {
            ret = composeFilter(termFilter(TYPE_FIELD, OidcConfigurationType.TENANT.name()),
                                termFilter(ORGANIZATION_ID_FIELD, orgId),
                                termFilter(APPLICATION_ID_FIELD, applicationId),
                                termFilter(TENANT_ID_FIELD, tenantId));
        }
        return ret;
    }
}
