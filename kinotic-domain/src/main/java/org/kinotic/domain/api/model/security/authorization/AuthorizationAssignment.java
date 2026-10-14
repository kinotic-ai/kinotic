package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationAssignment {
    private String id;
    private String roleId;
    private AuthorizationSubjectKind subjectKind;
    private String subjectId;
    private String resourceType;
    private AuthorizationSelector selector;
    private String resourceId;
    private AuthorizationEffect effect = AuthorizationEffect.ALLOW;
}
