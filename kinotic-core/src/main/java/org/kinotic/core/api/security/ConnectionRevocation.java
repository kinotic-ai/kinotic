package org.kinotic.core.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A revocation published to every node consuming it, which every node running the API gateway does: every
 * connection authenticated as the identity ends.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRevocation {

    private String identityId;

}
