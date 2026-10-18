package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentityType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

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

    /** The users among {@code ids} whose email or display name matches {@code searchText}. */
    public Future<Page<UserParticipantIdentity>> searchUsersById(String searchText, Collection<String> ids, Pageable pageable) {
        Validate.notBlank(searchText, "searchText cannot be blank");
        return findAll(pageable, b -> b.query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText)
                                                 .fields("email", "displayName")
                                                 .analyzeWildcard(true)))
                .filter(composeFilter(
                        termFilter("type", ParticipantIdentityType.USER.name()),
                        Query.of(f -> f.ids(i -> i.values(List.copyOf(ids))))))))))
                .map(page -> page.map(UserParticipantIdentity.class::cast));
    }

    public Future<Page<MachineParticipantIdentity>> findMachinesByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
                termFilter("type", ParticipantIdentityType.MACHINE.name()),
                scopeFilter(organizationId, applicationId))))
                .map(page -> page.map(MachineParticipantIdentity.class::cast));
    }

    /** The delegates owner {@code ownerId} authorized in the scope {@code (organizationId, applicationId)}. */
    public Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId,
                                                                            String organizationId,
                                                                            String applicationId,
                                                                            Pageable pageable) {
        Validate.notBlank(ownerId, "ownerId cannot be blank");
        return findAll(pageable, b -> b.query(composeFilter(
                termFilter("ownerId", ownerId),
                scopeFilter(organizationId, applicationId))))
                .map(page -> page.map(DelegatingParticipantIdentity.class::cast));
    }

    /** Every delegate owner {@code ownerId} authorized, in any scope. */
    public Future<Page<DelegatingParticipantIdentity>> findAllDelegatesByOwner(String ownerId, Pageable pageable) {
        Validate.notBlank(ownerId, "ownerId cannot be blank");
        return findAll(pageable, b -> b.query(termFilter("ownerId", ownerId)))
                .map(page -> page.map(DelegatingParticipantIdentity.class::cast));
    }

    /** The delegate owner {@code ownerId} authorized for client {@code clientKey} in the scope, or {@code null}. */
    public Future<DelegatingParticipantIdentity> findByOwnerAndClientKey(String ownerId,
                                                                         String clientKey,
                                                                         String organizationId,
                                                                         String applicationId) {
        Validate.notBlank(ownerId, "ownerId cannot be blank");
        Validate.notBlank(clientKey, "clientKey cannot be blank");
        return findFirst(b -> b.query(composeFilter(
                termFilter("ownerId", ownerId),
                termFilter("clientKey", clientKey),
                scopeFilter(organizationId, applicationId))))
                .map(DelegatingParticipantIdentity.class::cast);
    }

    /**
     * Structural scope filter against the typed scope fields on the document:
     * <ul>
     *   <li>both null → {@code organizationId} and {@code applicationId} must be missing: an account, or a platform machine or delegate.</li>
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
