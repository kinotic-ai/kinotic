package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

/**
 * What an organization's current billing period of its subscription is expected to cost: its usage so far, plus what
 * it runs and stores now kept up to the period's end. Amounts are in the smallest unit of {@link #currency}, as {@link PlanComponent} amounts are.
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
    /**
     * The usage of each meter by {@link #periodEnd}: the usage to date, plus what the organization runs and stores now
     * for the rest of the period.
     */
    private Map<UsageMeter, Double> projectedUsage = new EnumMap<>(UsageMeter.class);
    /** The plan's flat fees plus the projected usage beyond what the plan includes, before tax and discounts. */
    private long projectedAmount;
}
