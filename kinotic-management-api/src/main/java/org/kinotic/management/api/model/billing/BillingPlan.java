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
 * What an organization pays for Kinotic Cloud: a flat fee, metered usage, or both, each a {@link PlanComponent},
 * billed together every {@link #interval}. A plan never changes once created, since the subscriptions on it are billed
 * with its Stripe Prices: a new price is a new plan, and a plan no longer offered is {@link #archived}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingPlan implements Identifiable<String> {

    private String id;

    /** The name organizations choose the plan by, and their invoices show. */
    private String name;

    private String description;

    /**
     * The organization the plan is offered to alone, such as a negotiated price, or {@code null} for a plan every
     * organization may choose.
     */
    private String organizationId;

    /** The ISO 4217 code of the currency the plan bills in, in lowercase, such as {@code usd}. */
    private String currency;

    private BillingInterval interval;

    /** The plan's charges, at least one. */
    private List<PlanComponent> components = new ArrayList<>();

    /** Whether the plan is no longer offered; subscriptions already on it stay on it. */
    private boolean archived;

    /** The id of the plan's Stripe Product, set when the plan is created. */
    private String stripeProductId;

    private Date created;

    private Date updated;
}
