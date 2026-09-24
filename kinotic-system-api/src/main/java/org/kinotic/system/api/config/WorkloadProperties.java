package org.kinotic.system.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for the records of workload runs.
 * Accessible via {@code kinotic.systemApi.workload.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class WorkloadProperties {

    /**
     * How long (in days) the record of a run that has ended is kept, with its logs, before the
     * retention sweep deletes it.
     */
    private int retentionDays = 30;

}
