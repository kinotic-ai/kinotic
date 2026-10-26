package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A sign-in method of an {@link OrganizationParticipantIdentity} or an {@link ApplicationParticipantIdentity}:
 * the identity a provider asserts for the person, the {@code sub} claim under one OIDC configuration, bound to the
 * user it signs in. A user may hold any number of bindings beside its password. A provider identity is bound to at
 * most one user of each organization and one of each application, so a person signs in to several organizations and
 * applications with the same Google identity while the system's registration for Google is one configuration for
 * all of them. A system user signs in with a password alone and holds no binding.
 *
 * <p>The id is derived from the configuration, the subject and the user's scope ({@link #idOf}), so a
 * callback to one organization's or application's login resolves a binding by a get, which sees a binding the
 * moment it is written.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcBinding implements Identifiable<String> {

    private String id;

    /** The user the provider identity signs in. */
    private String identityId;

    /** The organization whose user, or whose application's user, is bound. */
    private String organizationId;

    /** The application whose user is bound, or {@code null} for an organization user. */
    private String applicationId;

    /** The OIDC configuration the provider identity was asserted under. */
    private String oidcConfigId;

    /** The provider's {@code sub} claim for the person. */
    private String subject;

    private Date created;

    /**
     * The id of the binding of {@code subject} under configuration {@code oidcConfigId} to a user of the
     * scope {@code (organizationId, applicationId)}: {@code <organizationId>/<oidcConfigId>:<subject>} for an
     * organization's user and {@code <organizationId>/<applicationId>/<oidcConfigId>:<subject>} for an application's.
     */
    public static String idOf(String oidcConfigId, String subject, String organizationId, String applicationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        String scope = applicationId != null ? organizationId + "/" + applicationId + "/" : organizationId + "/";
        return scope + oidcConfigId + ":" + subject;
    }
}
