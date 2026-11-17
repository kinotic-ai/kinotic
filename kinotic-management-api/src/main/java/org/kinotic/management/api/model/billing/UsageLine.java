package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The usage of one {@link UsageMeter} by one thing during the hour of a {@link UsageRecord}, such as the vCPU-hours of
 * one microservice.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UsageLine {

    private UsageMeter meter;

    /** The id of what used it, such as a microservice deployment's id or an application's id. */
    private String subjectId;

    /** The name the portal shows for what used it. */
    private String subjectName;

    /** How much was used, in the meter's {@link UsageMeter#getUnit() unit}. */
    private double quantity;
}
