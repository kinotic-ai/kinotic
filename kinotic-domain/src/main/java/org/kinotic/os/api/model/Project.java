package org.kinotic.os.api.model;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Project implements ApplicationScoped<String> {

    /**
     * The id of the project.
     * All project ids are unique throughout the entire system.
     */
    private String id;

    private String organizationId;

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
