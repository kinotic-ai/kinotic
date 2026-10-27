package org.kinotic.grind.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Which {@link JobRun}s a query returns. Each field narrows the runs; a field left null matches
 * every run.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JobRunFilter {

    /**
     * The organization that owns the runs.
     */
    private String organizationId;

    /**
     * The application the runs served.
     */
    private String applicationId;

    /**
     * The project the runs served.
     */
    private String projectId;

    /**
     * The status the runs are in.
     */
    private ExecutionStatus status;

    /**
     * The earliest time the runs started, inclusive.
     */
    private Date startedSince;

}
