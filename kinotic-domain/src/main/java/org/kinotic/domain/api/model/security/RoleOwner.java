package org.kinotic.domain.api.model.security;

import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;

/**
 * Who a {@link Role} belongs to: an organization, or one of its applications.
 *
 * @param organizationId the organization
 * @param applicationId  the application, or null for a role of the organization itself
 */
public record RoleOwner(String organizationId, String applicationId) {

    /**
     * The organization as the owner of its own roles.
     */
    public static RoleOwner organization(String organizationId) {
        return new RoleOwner(organizationId, null);
    }

    /**
     * The application as the owner of its own roles.
     */
    public static RoleOwner application(Application application) {
        return new RoleOwner(application.getOrganizationId(), application.getId());
    }

    /**
     * The store the owner's roles are granted in, named as its record is: the system's for an organization, and an
     * application's own.
     */
    public String store() {
        return applicationId == null ? AuthzStoreService.SYSTEM : AuthzGraphUtil.authzApplicationId(organizationId, applicationId);
    }

    /**
     * The type of the owner, the root of the tier its roles are granted in: {@code organization} or
     * {@code application}.
     */
    public String type() {
        return applicationId == null ? AuthzConstants.ORGANIZATION_TYPE : AuthzConstants.APPLICATION_TYPE;
    }
}
