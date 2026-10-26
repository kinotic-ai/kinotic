package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A person to add to one of the caller's organization's applications, as
 * {@link org.kinotic.management.api.services.security.MemberService#addApplicationUser} takes them.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class AddApplicationUserRequest {

    /** The address the person signs in with. */
    private String email;

    /** The person's display name, or {@code null}. */
    private String displayName;

    /** The application the person is added to, one of the caller's organization's. */
    private String applicationId;

    /**
     * The application's tenant the person joins, required unless the system manages the application's tenants
     * ({@link org.kinotic.domain.api.model.TenantManagement#SYSTEM}), which gives the person a tenant of their own,
     * when it is always {@code null}.
     */
    private String tenantId;

    /** Whether to email the person an invitation. */
    private boolean sendInvitation;
}
