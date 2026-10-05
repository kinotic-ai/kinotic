package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private List<String> oidcConfigurationIds;

    /**
     * The ways a user comes to belong to a tenant of this application: the mechanisms the application enables,
     * none for an application whose users share one tenant-less set of data. {@link OnboardingMechanism#TENANT_PER_USER}
     * applies to users created while it is enabled and excludes the other mechanisms; existing users are not
     * backfilled.
     */
    private Set<OnboardingMechanism> onboarding = new HashSet<>();

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

    private Date updated = null;

    public Application(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
