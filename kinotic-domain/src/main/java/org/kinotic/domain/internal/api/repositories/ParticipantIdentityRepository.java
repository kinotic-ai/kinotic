package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ParticipantIdentityRepository extends AbstractRepository<ParticipantIdentity> {

    public ParticipantIdentityRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_participant_identity", ParticipantIdentity.class, crudServiceTemplate);
    }

    public CompletableFuture<ParticipantIdentity> findByEmail(String email, String organizationId, String applicationId) {
        Validate.notBlank(email, "email cannot be blank");
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", DomainUtil.normalizeEmail(email)),
                scopeFilter(organizationId, applicationId))));
    }

    public CompletableFuture<ParticipantIdentity> findFirstOrgUserByEmail(String email) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", DomainUtil.normalizeEmail(email)),
                existsFilter("organizationId"),
                missingFilter("applicationId"))));
    }

    public CompletableFuture<ParticipantIdentity> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", DomainUtil.normalizeEmail(email))));
    }

    public CompletableFuture<Page<ParticipantIdentity>> findByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(scopeFilter(organizationId, applicationId)));
    }

    public CompletableFuture<Page<ParticipantIdentity>> searchByScope(String searchText,
                                                          String organizationId,
                                                          String applicationId,
                                                          Pageable pageable) {
        if (searchText == null || searchText.isEmpty()) {
            return findByScope(organizationId, applicationId, pageable);
        }
        return findAll(pageable, b -> b.query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText)
                                                 .fields("email", "displayName")
                                                 .analyzeWildcard(true)))
                .filter(scopeFilter(organizationId, applicationId))))));
    }

    public CompletableFuture<ParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                         String oidcConfigId,
                                                         String organizationId,
                                                         String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                scopeFilter(organizationId, applicationId))));
    }

    public CompletableFuture<ParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                existsFilter("organizationId"),
                missingFilter("applicationId"))));
    }

    /**
     * Structural scope filter against the typed scope fields on the document:
     * <ul>
     *   <li>both null → {@code organizationId} and {@code applicationId} must be missing (SYSTEM).</li>
     *   <li>only {@code organizationId} set → matches that org AND {@code applicationId} is missing (ORG)</li>
     *   <li>both set → matches both fields (APP)</li>
     * </ul>
     */
    private Query scopeFilter(String organizationId, String applicationId) {
        if (organizationId == null && applicationId == null) {
            return composeFilter(
                    missingFilter("organizationId"),
                    missingFilter("applicationId"));
        }
        if (applicationId == null) {
            return composeFilter(
                    termFilter("organizationId", organizationId),
                    missingFilter("applicationId"));
        }
        return composeFilter(
                termFilter("organizationId", organizationId),
                termFilter("applicationId", applicationId));
    }
}
