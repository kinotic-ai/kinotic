package org.kinotic.system.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.management.api.model.billing.UsageMeasurementType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Something an organization is billed for while it runs, at fixed {@link UsageMeasurementType#RUNNING_RESOURCE}
 * amounts per hour: one run of a microservice's VM, from when it {@link #started} to when it {@link #ended}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillableResource implements Identifiable<String> {

    /** The id of the run, such as the workload the VM runs as. */
    private String id;

    private String organizationId;

    /** The id of what the run is of, such as the microservice deployment, which usage lines name. */
    private String subjectId;

    /** The name the portal shows for what the run is of. */
    private String subjectName;

    /** What each hour of the run uses. */
    private List<MeteredAmount> amounts = new ArrayList<>();

    private Date started;

    /** When the run stopped, or {@code null} while it runs. */
    private Date ended;

    private Date created;

    private Date updated;
}
