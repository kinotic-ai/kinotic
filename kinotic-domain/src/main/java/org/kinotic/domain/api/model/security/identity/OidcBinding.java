package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A sign-in method of a {@link UserParticipantIdentity}: the identity a provider asserts for the person,
 * the {@code sub} claim under one OIDC configuration, bound to the user it signs in. A user may hold any
 * number of bindings beside its password. A provider identity is bound to at most one account, and to at
 * most one user of each application, so a person signs in to several applications with the same Google
 * identity while the platform's registration for Google is one configuration for all of them.
 *
 * <p>The id is derived from the configuration, the subject and the user's scope ({@link #idOf}), so a
 * callback resolves a binding by a get, which sees a binding the moment it is written.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcBinding implements Identifiable<String> {

    private String id;

    /** The user the provider identity signs in. */
    private String identityId;

    /** The organization of the application whose user is bound, or {@code null} for an account. */
    private String organizationId;

    /** The application whose user is bound, or {@code null} for an account. */
    private String applicationId;

    /** The OIDC configuration the provider identity was asserted under. */
    private String oidcConfigId;

    /** The provider's {@code sub} claim for the person. */
    private String subject;

    private Date created;

    /**
     * The id of the binding of {@code subject} under configuration {@code oidcConfigId} to a user of the
     * scope {@code (organizationId, applicationId)}: {@code <oidcConfigId>:<subject>} for an account, with
     * {@code <organizationId>/<applicationId>/} in front for an application's user.
     */
    public static String idOf(String oidcConfigId, String subject, String organizationId, String applicationId) {
        String scope = applicationId != null ? organizationId + "/" + applicationId + "/" : "";
        return scope + oidcConfigId + ":" + subject;
    }
}
