package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An organization's usage of Kinotic Cloud during one hour, as a {@link UsageLine} for each meter and thing that used
 * it. Lines of {@link UsageMeasurementType#POLLED_LEVEL} meters are added while the hour runs, and lines of
 * {@link UsageMeasurementType#RUNNING_RESOURCE} meters when the hour is settled.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UsageRecord implements Identifiable<String> {

    private String id;
    private String organizationId;
    /** The start of the hour. */
    private Date hour;
    private List<UsageLine> lines = new ArrayList<>();
    /**
     * When the hour was settled: its usage reported to Stripe if the organization was subscribed after its trial, and
     * left unbilled otherwise. {@code null} until then.
     */
    private Date settled;
    private Date created;
    private Date updated;
}
