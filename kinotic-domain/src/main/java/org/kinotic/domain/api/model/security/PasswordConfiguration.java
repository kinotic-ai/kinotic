package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * Sign-in with an email and a password at the login of one sign-in scope of an {@link Organization}, a sign-in
 * provider beside its {@link OwnedOidcConfiguration}s: the organization's own login, one of its applications' logins,
 * or the login of one tenant of one of its applications. A login takes a password only while a configuration of its
 * scope is enabled; a tenant's login also takes one while its application's is, as it offers the application's
 * providers. A scope has at most one, whose id is derived from the scope ({@link #idOf}).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PasswordConfiguration implements OrganizationScoped<String> {

    /** The name a login shows for an email and a password among its sign-in providers. */
    public static final String NAME = "Email and password";

    private String id;

    /** Owning organization: the one of the participant saving the configuration, which the service enforces. */
    private String organizationId;

    /** The application whose login, or whose tenant's login, the configuration is offered at; {@code null} for the organization's own login. */
    private String applicationId;

    /** The tenant of the application whose login the configuration is offered at; {@code null} unless it is a tenant's. */
    private String tenantId;

    /** Whether the login takes an email and a password. */
    private boolean enabled;

    /**
     * Whether a new person may sign up with an email and a password, the email verified by a link sent to it, from one
     * of the application's UIs. Taken only by an application's own configuration, and only while the system manages
     * the application's tenants, which gives each new user a tenant of its own.
     */
    private boolean createUserOnSignIn;

    private Date created;

    private Date updated;

    /**
     * The id of the configuration of a sign-in scope: the organization's own login when {@code applicationId} is
     * {@code null}, the application's when {@code tenantId} is {@code null}, else the tenant's.
     */
    public static String idOf(String organizationId, String applicationId, String tenantId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.isTrue(applicationId != null || tenantId == null, "A tenant's configuration names its application");
        String ret;
        if (applicationId == null) {
            ret = organizationId;
        } else if (tenantId == null) {
            ret = organizationId + ":" + applicationId;
        } else {
            ret = organizationId + ":" + applicationId + ":" + tenantId;
        }
        return ret;
    }
}
