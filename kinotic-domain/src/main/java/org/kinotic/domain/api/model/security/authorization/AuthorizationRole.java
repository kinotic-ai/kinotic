package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationRole {
    private String id;
    private String name;
    private java.util.List<String> permissions = new java.util.ArrayList<>();
}
