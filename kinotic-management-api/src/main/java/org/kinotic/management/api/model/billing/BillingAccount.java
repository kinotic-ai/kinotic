package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * An organization's standing with paying for Kinotic Cloud: its trial, the plan it subscribes to, and whether
 * payment is due. One account per organization, created with a free trial the first time it is read.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingAccount implements Identifiable<String> {

    /** The id of the organization the account belongs to. */
    private String id;

    private BillingStatus status;

    /** When the organization's free trial ends, or ended. */
    private Date trialEndsAt;

    /** The plan the organization subscribes to, or {@code null} when it has no subscription. */
    private String planId;

    /** When the current billing interval of the subscription started, or {@code null} without a subscription. */
    private Date currentPeriodStart;

    /** When the current billing interval of the subscription ends, or {@code null} without a subscription. */
    private Date currentPeriodEnd;

    /** When the organization's current or last subscription started, or {@code null} when it has had none. */
    private Date subscriptionStarted;

    /**
     * When the organization's last subscription ended, or {@code null} when none has. Usage before then is billed to
     * that subscription.
     */
    private Date subscriptionEnded;

    /** Since when payment is due, while the account is {@link BillingStatus#PAST_DUE}. */
    private Date pastDueSince;

    /** The id of the organization's Stripe Customer, once it has started a Checkout. */
    private String stripeCustomerId;

    /** The id of the organization's Stripe Subscription, once it has subscribed. */
    private String stripeSubscriptionId;

    /** The id of the Checkout Session the organization last opened, until a subscription starts. */
    private String stripeCheckoutSessionId;

    private Date created;

    private Date updated;
}
