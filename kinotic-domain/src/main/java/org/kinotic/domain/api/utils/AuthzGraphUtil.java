package org.kinotic.domain.api.utils;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.DelegateType;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The names the domain's participants, resources and grants carry in the authorization stores, and the tuples that
 * place them there.
 */
public final class AuthzGraphUtil {

    // The authorization types whose ids are unique only within an organization, qualified by it in the stores
    private static final Set<String> ORGANIZATION_LOCAL_TYPES = Set.of(AuthzConstants.APPLICATION_TYPE, AuthzConstants.PROJECT_TYPE);

    /**
     * Whether a participant may exercise a permission its grants give it: a delegate may only within the ceiling of
     * its {@link DelegateType}, and a delegate naming no known type may exercise none; any other participant may.
     *
     * @param participant the participant making a call
     * @param type        the resource type the permission is about, such as {@code project}
     * @param permission  the permission's short name, such as {@code can_edit}
     * @return true when the participant may exercise the permission
     */
    public static boolean withinDelegateCeiling(Participant participant, String type, String permission) {
        Map<String, String> metadata = participant.getMetadata() != null ? participant.getMetadata() : Map.of();
        boolean ret = true;
        if (metadata.containsKey(DomainUtil.ON_BEHALF_OF_METADATA_KEY)) {
            DelegateType delegateType = EnumUtils.getEnum(DelegateType.class, metadata.get(DomainUtil.DELEGATE_TYPE_METADATA_KEY));
            ret = delegateType != null && delegateType.permits(type, permission);
        }
        return ret;
    }

    /**
     * The user a participant acts as in the authorization stores, in {@code user:id} form: a delegate acts as its
     * owner would, and any other participant as itself. An organization's user belongs to that organization alone,
     * so a grant made in one organization never applies in another.
     *
     * @param participant the participant making a call
     * @return the user whose grants answer for it
     */
    public static String authzUser(Participant participant) {
        Map<String, String> metadata = participant.getMetadata() != null ? participant.getMetadata() : Map.of();
        String owner = metadata.get(DomainUtil.ON_BEHALF_OF_METADATA_KEY);
        return authzUser(owner != null ? owner : participant.getId());
    }

    /**
     * The user an identity is in the authorization stores, {@code user:<identityId>}.
     *
     * @param identityId the identity
     * @return the identity's user
     */
    public static String authzUser(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return AuthzUtil.object(AuthzConstants.USER_TYPE, identityId);
    }

    /**
     * The tuple making an organization's user a member of the organization, which a grant to the organization's
     * members reaches.
     *
     * @param organizationUserId the organization's user
     * @param organizationId     the organization
     * @return the membership tuple
     */
    public static RelationshipTuple organizationMember(String organizationUserId, String organizationId) {
        return new RelationshipTuple(authzUser(organizationUserId),
                                     AuthzConstants.MEMBER_RELATION,
                                     AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationId));
    }

    /**
     * Who a grant to a subject is made to in the authorization stores: a user as {@code user:<id>}, a group as its
     * members, {@code group:<id>#member}.
     *
     * @param subject the subject, named as the stores hold it
     * @return the user or userset
     */
    public static String authzSubject(Subject subject) {
        String ret;
        if (subject.type() == SubjectType.USER) {
            ret = AuthzUtil.object(AuthzConstants.USER_TYPE, subject.id());
        } else {
            ret = AuthzUtil.object(AuthzConstants.GROUP_TYPE, subject.id()) + "#" + AuthzConstants.MEMBER_RELATION;
        }
        return ret;
    }

    /**
     * The id an object of the type has in the authorization stores. An application's and a project's id is unique
     * only within its organization, so theirs is qualified as {@code <organizationId>.<id>}; every other type's id
     * is unique on its own and kept as it is.
     *
     * @param type           the object's type
     * @param organizationId the organization the object belongs to; required for an application or a project
     * @param id             the object's id as its organization knows it
     * @return the object's id in the stores
     * @throws IllegalArgumentException when an application or a project is named without an organization
     */
    public static String authzId(String type, String organizationId, String id) {
        Validate.notBlank(id, "id cannot be blank");
        String ret = id;
        if (ORGANIZATION_LOCAL_TYPES.contains(type)) {
            Validate.isTrue(StringUtils.isNotBlank(organizationId), "an organization is required to name the %s %s", type, id);
            ret = organizationId + "." + id;
        }
        return ret;
    }

    /**
     * The id an organization knows an object of the type by, given its id in the authorization stores: the
     * inverse of {@link #authzId}.
     *
     * @param type    the object's type
     * @param authzId the object's id in the stores
     * @return the object's id within its organization
     */
    public static String localId(String type, String authzId) {
        // an organization id holds no '.', so the first one ends it
        return ORGANIZATION_LOCAL_TYPES.contains(type) ? authzId.substring(authzId.indexOf('.') + 1) : authzId;
    }

    /**
     * The id an application has in the authorization stores, which also names the application's own store.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application's id within it
     * @return {@code <organizationId>.<applicationId>}
     */
    public static String authzApplicationId(String organizationId, String applicationId) {
        return authzId(AuthzConstants.APPLICATION_TYPE, organizationId, applicationId);
    }

    /**
     * The application object of the authorization stores, {@code application:<organizationId>.<applicationId>}.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application's id within it
     * @return the object
     */
    public static String authzApplication(String organizationId, String applicationId) {
        return AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, authzApplicationId(organizationId, applicationId));
    }

    /**
     * The tuple naming an object's parent, which a grant made on the parent reaches the object through:
     * {@code <parent> <parent type> <child>}.
     *
     * @param parent the parent object, in {@code type:id} form
     * @param child  the child object, in {@code type:id} form
     * @return the containment tuple
     */
    public static RelationshipTuple containment(String parent, String child) {
        return new RelationshipTuple(parent, AuthzUtil.typeOf(parent), child);
    }

    /**
     * The tuples an application's user holds in its application's own store: the user is an end user of the
     * application, and, for a user of a tenant, the tenant is placed under the application with the user as a
     * member. The system's store holds the first of them, the end user tuple, alone.
     *
     * @param user an application's user
     * @return the tuples, the end user tuple first
     */
    public static List<RelationshipTuple> applicationUserTuples(ApplicationParticipantIdentity user) {
        String me = authzUser(user.getId());
        String application = authzApplication(user.getOrganizationId(), user.getApplicationId());
        List<RelationshipTuple> ret = new ArrayList<>(List.of(new RelationshipTuple(me, AuthzConstants.END_USER_RELATION, application)));
        if (user.getTenantId() != null) {
            String tenant = AuthzUtil.object(AuthzConstants.TENANT_TYPE, user.getTenantId());
            ret.add(containment(application, tenant));
            ret.add(new RelationshipTuple(me, AuthzConstants.MEMBER_RELATION, tenant));
        }
        return ret;
    }

    /**
     * The two edges of a definition within a tenant, the object a tenant's user is checked on for the definition's
     * rows: the definition's, and the tenant's. No store holds tuples for the object, so a check on it is given
     * the edges.
     *
     * @param definitionId the definition's id
     * @param tenantId     the tenant's id within its application
     * @return the edges to give the check
     */
    public static List<RelationshipTuple> tenantDefinitionEdges(String definitionId, String tenantId) {
        String object = AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(definitionId, tenantId));
        return List.of(new RelationshipTuple(AuthzUtil.object(AuthzConstants.ENTITY_DEFINITION_TYPE, definitionId), AuthzConstants.DEFINITION_RELATION, object),
                       new RelationshipTuple(AuthzUtil.object(AuthzConstants.TENANT_TYPE, tenantId), AuthzConstants.TENANT_TYPE, object));
    }

    /**
     * The object a resource named as its organization knows it is in the authorization stores, in {@code type:id}
     * form.
     *
     * @param organizationId the organization the resource belongs to
     * @param resource       the resource
     * @return the object
     */
    public static String authzObject(String organizationId, Resource resource) {
        return AuthzUtil.object(resource.type(), authzId(resource.type(), organizationId, resource.id()));
    }

    /**
     * A grant read from the authorization stores, named as the resource's organization knows it: its resource by
     * the resource's local id.
     *
     * @param grant the grant as the stores hold it
     * @return the grant with local ids
     */
    public static Grant localGrant(Grant grant) {
        Resource resource = grant.resource();
        return new Grant(grant.id(), grant.roleId(), grant.subject(), new Resource(resource.type(), localId(resource.type(), resource.id())));
    }

    /**
     * An access explanation read from the authorization stores, its grants named as {@link #localGrant} names them.
     *
     * @param explanation the explanation as the stores answer it
     * @return the explanation with local ids
     */
    public static AccessExplanation localExplanation(AccessExplanation explanation) {
        return new AccessExplanation(explanation.allowed(), explanation.through().stream().map(AuthzGraphUtil::localGrant).toList());
    }

    private AuthzGraphUtil() {
    }
}
