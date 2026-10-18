package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
     * Who creates and administers this application's tenants. Under {@link TenantManagement#PLATFORM}
     * every APPLICATION-scope {@link ParticipantIdentity} created for the application receives a tenant
     * of its own; it applies to users created while it is in force, and existing users are not
     * backfilled.
     */
    private TenantManagement tenantManagement = TenantManagement.TENANT_ADMINISTRATORS;

    /**
     * Name of the UI whose site this application's browser flows return to, such as its OAuth consent
     * page: one of the application's published UIs, or {@code null} until the owner designates one.
     */
    private String primaryUiId;

    /**
     * Where the {@link #primaryUiId primary UI}'s site is served, or {@code null} while none is designated. Set
     * by the platform when the owner designates the primary UI; a value a caller saves is replaced.
     */
    private String primaryUiUrl;

    /**
     * The platform's social registrations this application's login offers, by provider kind, in the order
     * to present them: Google, Microsoft. The application's users sign in with Kinotic's registration at
     * the provider, so the application registers nothing of its own; the organization's own providers
     * for the application are its {@code ApplicationOidcConfiguration}s. Empty offers none.
     */
    private List<OidcProviderKind> socialProviders = new ArrayList<>();

    private Date updated = null;

    public Application(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
