package org.kinotic.os.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Organization implements Identifiable<String> {

    private String id;

    private String name;

    private String slug;

    private String description;

    private List<String> oidcConfigurationIds;

    private String createdBy;

    private Date created;

    private Date updated;

}
