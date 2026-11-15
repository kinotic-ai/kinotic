package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An organization's use of Kinotic Cloud during one hour, by {@link UsageMeter}. The usage is measured every five
 * minutes from the workload VMs the organization has running then, each measurement standing for those five minutes.
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
    /** The usage of each meter during the hour. */
    private Map<UsageMeter, Double> quantities = new EnumMap<>(UsageMeter.class);
    /** The five-minute slots of the hour measured so far, numbered from 0 at the top of the hour. */
    private List<Integer> measuredSlots = new ArrayList<>();
    /**
     * When the hour was settled: its usage reported to Stripe if the organization was subscribed after its trial, and
     * left unbilled otherwise. {@code null} until then.
     */
    private Date settled;
    private Date created;
    private Date updated;
}
