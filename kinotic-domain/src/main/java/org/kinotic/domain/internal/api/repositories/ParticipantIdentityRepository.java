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
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParticipantIdentityRepository extends AbstractRepository<ParticipantIdentity> {

    // the most organizations one email is a member of
    private static final int MAX_ORGANIZATIONS_PER_EMAIL = 100;

    // a disabled user is left as it is, so an administrator's enabled=false written meanwhile is never overwritten
    private static final String CLEAR_PENDING_SCRIPT = """
            if (ctx._source.pending != true || ctx._source.enabled != true) {
                ctx.op = 'noop'
            } else {
                ctx._source.pending = false;
                ctx._source.updated = params.updated;
                if (params.displayName != null) {
                    ctx._source.displayName = params.displayName
                }
            }""";

    public ParticipantIdentityRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_participant_identity", ParticipantIdentity.class, crudServiceTemplate);
    }

    /**
     * Makes the pending user {@code id} one that signs in, which exactly one caller does, however many race for the
     * user: true when this call cleared the flag, false when the user was no longer pending or is disabled, in which
     * case the user is left as it is. The change is visible to search when the future completes.
     *
     * @param id          the user
     * @param displayName the name the user gives itself as it signs in, or {@code null} to keep the one it has
     */
    public Future<Boolean> clearPending(String id, String displayName) {
        Validate.notBlank(id, "id cannot be blank");
        Map<String, Object> params = new HashMap<>();
        params.put("updated", new Date());
        params.put("displayName", displayName);
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, CLEAR_PENDING_SCRIPT, params);
    }

    /** The system user with the email, or {@code null}. */
    public Future<SystemParticipantIdentity> findSystemUserByEmail(String email) {
        return findUserByEmail(ParticipantIdentityType.SYSTEM_USER, email, null)
                .map(SystemParticipantIdentity.class::cast);
    }

    /** The user of organization {@code organizationId} with the email, or {@code null}. */
    public Future<OrganizationParticipantIdentity> findOrganizationUserByEmail(String email, String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findUserByEmail(ParticipantIdentityType.ORGANIZATION_USER, email, termFilter("organizationId", organizationId))
                .map(OrganizationParticipantIdentity.class::cast);
    }

    /** The users with the email of every organization. */
    public Future<List<OrganizationParticipantIdentity>> findOrganizationUsersByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return findAll(Pageable.ofSize(MAX_ORGANIZATIONS_PER_EMAIL),
                       b -> b.query(composeFilter(termFilter("type", ParticipantIdentityType.ORGANIZATION_USER.name()),
                                                  termFilter("email", DomainUtil.normalizeEmail(email)))))
                .map(page -> page.getContent().stream().map(OrganizationParticipantIdentity.class::cast).toList());
    }

    /** The user of application {@code applicationId} of organization {@code organizationId} with the email, or {@code null}. */
    public Future<ApplicationParticipantIdentity> findApplicationUserByEmail(String email, String organizationId, String applicationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findUserByEmail(ParticipantIdentityType.APPLICATION_USER, email, applicationFilter(organizationId, applicationId))
                .map(ApplicationParticipantIdentity.class::cast);
    }

    private Future<ParticipantIdentity> findUserByEmail(ParticipantIdentityType type, String email, Query scope) {
        Validate.notBlank(email, "email cannot be blank");
        Query typeAndEmail = composeFilter(termFilter("type", type.name()),
                                           termFilter("email", DomainUtil.normalizeEmail(email)));
        return findFirst(b -> b.query(scope == null ? typeAndEmail : composeFilter(typeAndEmail, scope)));
    }

    /** The system users. */
    public Future<Page<SystemParticipantIdentity>> findSystemUsers(Pageable pageable) {
        return findUsers(ParticipantIdentityType.SYSTEM_USER, null, pageable)
                .map(page -> page.map(SystemParticipantIdentity.class::cast));
    }

    /** The system users whose email or display name matches {@code searchText}; blank text lists them all. */
    public Future<Page<SystemParticipantIdentity>> searchSystemUsers(String searchText, Pageable pageable) {
        return searchUsers(searchText, ParticipantIdentityType.SYSTEM_USER, null, pageable)
                .map(page -> page.map(SystemParticipantIdentity.class::cast));
    }

    /** The users of application {@code applicationId} of organization {@code organizationId}. */
    public Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String organizationId, String applicationId, Pageable pageable) {
        return findUsers(ParticipantIdentityType.APPLICATION_USER, applicationFilter(organizationId, applicationId), pageable)
                .map(page -> page.map(ApplicationParticipantIdentity.class::cast));
    }

    /** The users of the application whose email or display name matches {@code searchText}; blank text lists them all. */
    public Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText,
                                                                                  String organizationId,
                                                                                  String applicationId,
                                                                                  Pageable pageable) {
        return searchUsers(searchText, ParticipantIdentityType.APPLICATION_USER, applicationFilter(organizationId, applicationId), pageable)
                .map(page -> page.map(ApplicationParticipantIdentity.class::cast));
    }

    /** The users of organization {@code organizationId}. */
    public Future<Page<OrganizationParticipantIdentity>> findOrganizationUsers(String organizationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findUsers(ParticipantIdentityType.ORGANIZATION_USER, termFilter("organizationId", organizationId), pageable)
                .map(page -> page.map(OrganizationParticipantIdentity.class::cast));
    }

    /** The users of the organization whose email or display name matches {@code searchText}; blank text lists them all. */
    public Future<Page<OrganizationParticipantIdentity>> searchOrganizationUsers(String searchText, String organizationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return searchUsers(searchText, ParticipantIdentityType.ORGANIZATION_USER, termFilter("organizationId", organizationId), pageable)
                .map(page -> page.map(OrganizationParticipantIdentity.class::cast));
    }

    private Future<Page<ParticipantIdentity>> findUsers(ParticipantIdentityType type, Query scope, Pageable pageable) {
        Query ofType = termFilter("type", type.name());
        return findAll(pageable, b -> b.query(scope == null ? ofType : composeFilter(ofType, scope)));
    }

    private Future<Page<ParticipantIdentity>> searchUsers(String searchText, ParticipantIdentityType type, Query scope, Pageable pageable) {
        Future<Page<ParticipantIdentity>> ret;
        if (searchText == null || searchText.isBlank()) {
            ret = findUsers(type, scope, pageable);
        } else {
            Query ofType = termFilter("type", type.name());
            ret = findAll(pageable, b -> b.query(Query.of(q -> q.bool(bq -> bq
                    .must(m -> m.queryString(qs -> qs.query(searchText)
                                                     .fields("email", "displayName")
                                                     .analyzeWildcard(true)))
                    .filter(scope == null ? ofType : composeFilter(ofType, scope))))));
        }
        return ret;
    }

    private Query applicationFilter(String organizationId, String applicationId) {
        return composeFilter(termFilter("organizationId", organizationId),
                             termFilter("applicationId", applicationId));
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
     *   <li>both null → {@code organizationId} and {@code applicationId} must be missing (SYSTEM)</li>
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
