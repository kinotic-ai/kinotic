package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * What a {@link UsageMeter} is, for showing its usage: its name, unit, group and how it is measured.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UsageMeterDescriptor {

    private UsageMeter meter;

    private String displayName;

    private String unit;

    private UsageCategory category;

    private UsageMeasurementType measurementType;
}
