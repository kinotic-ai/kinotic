package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.kinotic.idl.api.schema.FunctionDefinition;
import java.util.ArrayList;
import java.util.List;

/** Generated from app service decorators by kinotic sync, never edited by administrators. */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class ApplicationServiceContract {
    private String namespace;
    private String name;
    private String zone;
    private String version = "1.0.0";
    private List<FunctionDefinition> functions = new ArrayList<>();
}
