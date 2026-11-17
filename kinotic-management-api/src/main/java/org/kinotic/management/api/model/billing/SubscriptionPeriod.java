package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * The time one of an organization's Stripe subscriptions covers: usage during it is billed to that subscription.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class SubscriptionPeriod {

    /** The id of the Stripe Subscription. */
    private String stripeSubscriptionId;

    /** When the subscription started. */
    private Date started;

    /** When the subscription ended, or {@code null} while it runs. */
    private Date ended;
}
