package org.kinotic.domain.api.model.security.authorization;

import lombok.Value;

/** Safe identity picker data, without credentials or authentication configuration. */
@Value
public class AuthorizationIdentityOption {
    String id;
    String label;
}
