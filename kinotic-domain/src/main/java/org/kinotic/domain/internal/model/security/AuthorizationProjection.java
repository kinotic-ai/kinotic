package org.kinotic.domain.internal.model.security;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.util.List;
import org.kinotic.core.api.crud.Identifiable;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationProjection implements Identifiable<String> {
    private String id;
    private List<AuthorizationTuple> tuples = List.of();
    private List<AuthorizationTuple> candidates = List.of();
}
