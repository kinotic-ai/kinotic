package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationPolicy implements org.kinotic.core.api.crud.Identifiable<String> {
    private String id;
    private AuthorizationScope scope;
    private long revision;
    private java.util.List<String> administrators = new java.util.ArrayList<>();
    private java.util.List<AuthorizationRole> roles = new java.util.ArrayList<>();
    private java.util.List<AuthorizationGroup> groups = new java.util.ArrayList<>();
    private java.util.List<AuthorizationAssignment> assignments = new java.util.ArrayList<>();
}
