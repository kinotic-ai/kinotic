package org.kinotic.idl.internal.support.authz;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A resource with its own id and the id of the application containing it.
 */
@Getter
@Setter
@Accessors(chain = true)
public class TestProject {
    private String id;
    private String applicationId;
    private String name;
}
