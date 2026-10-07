package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationPolicyView {
    private AuthorizationPolicy policy;
    private java.util.List<AuthorizationPermission> permissions = new java.util.ArrayList<>();
    private boolean pending;
    private long modelRevision;
}
