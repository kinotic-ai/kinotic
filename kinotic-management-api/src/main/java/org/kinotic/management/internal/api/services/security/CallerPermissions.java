package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * What the calling member of an organization holds on the organization's resources, through any grant that reaches
 * them. A delegate holds what its owner holds within the ceiling of its type.
 */
@Component
@RequiredArgsConstructor
public class CallerPermissions {

    private final SecurityContext securityContext;
    private final RelationshipService relationships;

    /**
     * Whether the caller holds a permission on one resource of its organization.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_read}
     * @param id         the resource's id within the caller's organization
     * @return true when the caller holds the permission on the resource
     */
    public Future<Boolean> holds(String type, String permission, String id) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        Validate.notBlank(id, "id cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        return holds(participant, type, permission, AuthzGraphUtil.authzObject(participant.getOrganizationId(), new Resource(type, id)));
    }

    /**
     * The resources of a type the caller holds a permission on.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_read}
     * @return the ids of the resources within the caller's organization
     */
    public Future<List<String>> listAccessible(String type, String permission) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        Future<List<String>> ret;
        if (AuthzGraphUtil.withinDelegateCeiling(participant, type, permission)) {
            ret = relationships.listObjects(SYSTEM,
                                            AuthzGraphUtil.authzUser(participant),
                                            AuthzUtil.permissionName(type, permission), type,
                                            Consistency.MINIMIZE_LATENCY)
                               .map(objects -> objects.stream().map(object -> AuthzGraphUtil.localId(type, AuthzNameUtil.idOf(object))).toList());
        } else {
            ret = Future.succeededFuture(List.of());
        }
        return ret;
    }

    /**
     * The resources of a type the caller holds a permission on, as {@link #listAccessible} lists them, or null when
     * the caller holds the permission on its organization, and so on every resource of the type in it.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_read}
     * @return the ids of the resources, or null for every one in the caller's organization
     */
    public Future<List<String>> findAccessibleIds(String type, String permission) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        // a caller holding the permission throughout the organization is answered with one check, without a listing
        return holds(participant, type, permission, AuthzNameUtil.object(AuthzConstants.ORGANIZATION_TYPE, participant.getOrganizationId()))
                .compose(held -> held ? Future.succeededFuture(null) : listAccessible(type, permission));
    }

    /**
     * Whether the caller holds a permission on every resource of a type in one application of its organization,
     * through a grant on the application or above it.
     *
     * @param applicationId the application
     * @param type          the resource type, one an application contains
     * @param permission    the permission's short name, such as {@code can_read}
     * @return true when the caller holds the permission throughout the application
     */
    public Future<Boolean> holdsThroughout(String applicationId, String type, String permission) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        return holds(participant, type, permission, AuthzGraphUtil.authzApplication(participant.getOrganizationId(), applicationId));
    }

    private Future<Boolean> holds(OrganizationParticipant participant, String type, String permission, String object) {
        Future<Boolean> ret;
        if (AuthzGraphUtil.withinDelegateCeiling(participant, type, permission)) {
            RelationshipTuple held = new RelationshipTuple(AuthzGraphUtil.authzUser(participant), AuthzUtil.permissionName(type, permission), object);
            ret = relationships.check(SYSTEM, held, Consistency.MINIMIZE_LATENCY);
        } else {
            ret = Future.succeededFuture(false);
        }
        return ret;
    }

    private OrganizationParticipant requireOrgParticipant() {
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }
}
