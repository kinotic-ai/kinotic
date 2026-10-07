package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationModel implements org.kinotic.core.api.crud.Identifiable<String> {
    private String id;
    private String storeId;
    private String modelId;
    private long revision;
    private boolean pending;
    private java.util.List<String> resourceTypes = new java.util.ArrayList<>();
}
