package org.kinotic.core.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A revocation published to every node consuming it, which every node running the API gateway does: every
 * connection authenticated as the identity, or established on the browser login, that it names ends.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public final class ConnectionRevocation {

    /** Whether {@link #id} names an identity or a browser login. */
    private ConnectionRevocationType type;

    /** The id of the identity or of the browser login whose connections end. */
    private String id;

}
