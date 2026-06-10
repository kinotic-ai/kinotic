package org.kinotic.os.api.model.iam;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The accept methods available for an invitation into a scope: whether password (LOCAL)
 * acceptance is offered, and every OIDC provider configured for the scope. The invitee
 * chooses among them on the accept page.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class InviteOptions {

    /**
     * Whether the invitee may accept by setting a password. Always true today; carried on
     * the contract so a per-scope toggle can be introduced without a breaking change.
     */
    private boolean localEnabled;

    /** OIDC providers configured for the scope the invitee would join; may be empty. */
    private List<OidcProviderOption> providers;
}
