package org.kinotic.system.internal.api.model;

import org.kinotic.management.api.model.billing.UsageLine;

/**
 * The level of an organization's usage that a usage source read, as the usage it stands for over one hour.
 *
 * @param organizationId the organization using it
 * @param line           the meter, what uses it, and the quantity it stands for over an hour
 */
public record UsageReading(String organizationId, UsageLine line) {
}
