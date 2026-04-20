package org.kinotic.os.api.model;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Application implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    private String description;

    private List<String> oidcConfigurationIds;

    private Date updated = null;

    public Application(String id, String description) {
        this.id = id;
        this.description = description;
    }
}
