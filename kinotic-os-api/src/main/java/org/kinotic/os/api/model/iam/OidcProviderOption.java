package org.kinotic.os.api.model.iam;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.iam.OidcProviderKind;

/**
 * One OIDC provider an invitee can accept an invitation with. Mirrors the
 * {@code {id, name, provider}} shape the login endpoints use for provider lists.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderOption {

    /** Id of the OIDC configuration to start the accept flow against. */
    private String id;

    /** Display name configured for the provider. */
    private String name;

    /** The provider kind, for button branding (serialized as its key, e.g. {@code "google"}). */
    private OidcProviderKind provider;
}
