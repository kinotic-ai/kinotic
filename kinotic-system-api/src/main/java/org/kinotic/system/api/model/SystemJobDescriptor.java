package org.kinotic.system.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A grind job the platform's operators can start on demand, as the console lists it.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class SystemJobDescriptor {

    /**
     * The name identifying the job, which is also the name recorded on each of its runs.
     */
    private String name;

    /**
     * The version of the job's definition, recorded on each of its runs.
     */
    private String version;

    /**
     * What the job does.
     */
    private String description;

}
