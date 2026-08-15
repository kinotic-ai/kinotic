package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.ParticipantIdentity;
import org.kinotic.domain.api.model.security.ParticipantIdentityType;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class ParticipantIdentityRepository extends AbstractRepository<ParticipantIdentity> {

    public ParticipantIdentityRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_participant_identity", ParticipantIdentity.class, crudServiceTemplate);
    }

    public Future<UserParticipantIdentity> findByEmail(String email, String organizationId, String applicationId) {
        Validate.notBlank(email, "email cannot be blank");
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", DomainUtil.normalizeEmail(email)),
                scopeFilter(organizationId, applicationId))))
                .map(UserParticipantIdentity.class::cast);
    }

    public Future<UserParticipantIdentity> findFirstOrgUserByEmail(String email) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", DomainUtil.normalizeEmail(email)),
                existsFilter("organizationId"),
                missingFilter("applicationId"))))
                .map(UserParticipantIdentity.class::cast);
    }

    public Future<UserParticipantIdentity> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", DomainUtil.normalizeEmail(email))))
                .map(UserParticipantIdentity.class::cast);
    }

    /** Users only — delegates share their owner's scope and must not appear in member listings. */
    public Future<Page<UserParticipantIdentity>> findUsersByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
                termFilter("type", ParticipantIdentityType.USER.name()),
                scopeFilter(organizationId, applicationId))))
                .map(page -> page.map(UserParticipantIdentity.class::cast));
    }

    /** Users only — delegates share their owner's scope and must not appear in member listings. */
    public Future<Page<UserParticipantIdentity>> searchUsersByScope(String searchText,
                                                                    String organizationId,
                                                                    String applicationId,
                                                                    Pageable pageable) {
        if (searchText == null || searchText.isEmpty()) {
            return findUsersByScope(organizationId, applicationId, pageable);
        }
        return findAll(pageable, b -> b.query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText)
                                                 .fields("email", "displayName")
                                                 .analyzeWildcard(true)))
                .filter(composeFilter(
                        termFilter("type", ParticipantIdentityType.USER.name()),
                        scopeFilter(organizationId, applicationId)))))))
                .map(page -> page.map(UserParticipantIdentity.class::cast));
    }

    public Future<Page<MachineParticipantIdentity>> findMachinesByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
                termFilter("type", ParticipantIdentityType.MACHINE.name()),
                scopeFilter(organizationId, applicationId))))
                .map(page -> page.map(MachineParticipantIdentity.class::cast));
    }

    public Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId, Pageable pageable) {
        Validate.notBlank(ownerId, "ownerId cannot be blank");
        return findAll(pageable, b -> b.query(termFilter("ownerId", ownerId)))
                .map(page -> page.map(DelegatingParticipantIdentity.class::cast));
    }

    public Future<DelegatingParticipantIdentity> findByOwnerAndClientKey(String ownerId, String clientKey) {
        Validate.notBlank(ownerId, "ownerId cannot be blank");
        Validate.notBlank(clientKey, "clientKey cannot be blank");
        return findFirst(b -> b.query(composeFilter(
                termFilter("ownerId", ownerId),
                termFilter("clientKey", clientKey))))
                .map(DelegatingParticipantIdentity.class::cast);
    }

    public Future<UserParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                              String oidcConfigId,
                                                              String organizationId,
                                                              String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                scopeFilter(organizationId, applicationId))))
                .map(UserParticipantIdentity.class::cast);
    }

    public Future<UserParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                existsFilter("organizationId"),
                missingFilter("applicationId"))))
                .map(UserParticipantIdentity.class::cast);
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
