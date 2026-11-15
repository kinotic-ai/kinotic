package org.kinotic.system.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.billing.UsageMeter;

/**
 * An amount of a {@link UsageMeter} used each hour, such as 2 vCPU-hours by a VM with two vCPUs.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class MeteredAmount {

    private UsageMeter meter;

    private double perHour;
}
