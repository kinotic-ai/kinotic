package org.kinotic.core.api.domain;

import java.util.Date;
import org.kinotic.core.api.services.crud.Identifiable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Project implements Identifiable<String> {

    /**
     * The id of the project.
     * All project ids are unique throughout the entire system.
     */
    private String id;

    /**
     * The id of the application that this project belongs to.
     * All application ids are unique throughout the entire system.
     */
    private String applicationId;
    
    /**
     * The name of the project.
     */
    private String name;

    /**
     * The description of the project.
     */
    private String description;

    /**
     * The source of truth for the project.
     */
    private ProjectType sourceOfTruth;

    /**
     * The date and time the project was updated.
     */
    private Date updated;

}
