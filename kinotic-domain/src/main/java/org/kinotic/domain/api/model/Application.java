package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Date;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Application implements OrganizationScoped<String> {

    /**
     * The slugified {@link #name}, minted at creation
     */
    private String id;

    private String organizationId;

    private String name;

    private String description;

    /**
     * Who creates and administers this application's tenants. Under {@link TenantManagement#SYSTEM}
     * every APPLICATION-scope {@link ParticipantIdentity} created for the application receives a tenant
     * of its own; it applies to users created while it is in force, and existing users are not
     * backfilled.
     */
    private TenantManagement tenantManagement = TenantManagement.TENANT_ADMINISTRATORS;

    /**
     * Name of the UI whose site this application's browser flows return to, such as its OAuth consent
     * page: one of the application's published UIs, or {@code null} while none is designated. A UI whose site is
     * minted while none is designated becomes the primary UI; the owner can designate another.
     */
    private String primaryUiId;

    /**
     * Where the {@link #primaryUiId primary UI}'s site is served, or {@code null} while none is designated. Set
     * by the platform when the primary UI is designated; a value a caller saves is replaced.
     */
    private String primaryUiUrl;

    /**
     * Whether a person may sign up as a new user of the application with an email and a password, the email
     * verified by a link sent to it, from one of the application's UIs. Taken only while the system manages the
     * application's tenants, which gives each new user a tenant of its own; a user of an application whose tenants
     * their administrators manage joins a tenant through an invitation or the tenant's own login. The
     * application's providers sign people up as each one's {@code createUserOnSignIn} says.
     */
    private boolean passwordSignUp;

    private Date updated = null;

    public Application(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
