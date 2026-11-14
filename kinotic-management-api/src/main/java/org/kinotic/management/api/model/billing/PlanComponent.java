package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * One charge of a {@link BillingPlan}: a flat fee each billing interval, or a price for each unit of a meter used.
 * Amounts are in the smallest unit of the plan's currency, as Stripe takes them: cents for {@code usd}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PlanComponent {

    private PlanComponentType type;

    /** The fee each billing interval, for a {@link PlanComponentType#FLAT} component. */
    private Long amount;

    /** What is measured, for a {@link PlanComponentType#METERED} component. */
    private UsageMeter meter;

    /**
     * The price of each unit of {@link #meter} beyond {@link #includedQuantity}, for a
     * {@link PlanComponentType#METERED} component; it may hold a fraction of the smallest unit, such as {@code 0.5}
     * cents, to at most 12 decimal places.
     */
    private BigDecimal unitAmount;

    /**
     * How many units of {@link #meter} each billing interval are free, for a {@link PlanComponentType#METERED}
     * component.
     */
    private long includedQuantity;

    /** The id of the component's Stripe Price, set when its plan is created. */
    private String stripePriceId;
}
