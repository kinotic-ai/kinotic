package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

/**
 * What an organization's current billing period of its subscription is expected to cost, projecting its usage so far
 * over the whole period. Amounts are in the smallest unit of {@link #currency}, as {@link PlanComponent} amounts are.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingForecast {

    private String planId;
    private String currency;
    private Date periodStart;
    private Date periodEnd;
    /** The usage of each meter since {@link #periodStart}. */
    private Map<UsageMeter, Double> usageToDate = new EnumMap<>(UsageMeter.class);
    /** The usage of each meter by {@link #periodEnd} if it goes on at the rate it has since {@link #periodStart}. */
    private Map<UsageMeter, Double> projectedUsage = new EnumMap<>(UsageMeter.class);
    /** The plan's flat fees plus the projected usage beyond what the plan includes, before tax and discounts. */
    private long projectedAmount;
}
