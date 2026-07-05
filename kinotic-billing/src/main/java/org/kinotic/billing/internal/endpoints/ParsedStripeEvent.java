package org.kinotic.billing.internal.endpoints;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * The minimal envelope extracted from a verified Stripe webhook payload — just enough to
 * persist the idempotency record and route processing. Handles both v1 snapshot events
 * ({@code data.object.id}, {@code account}) and v2 thin events ({@code related_object.id},
 * {@code context}).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ParsedStripeEvent {

    private String id;

    private String type;

    private Boolean livemode;

    /** Connected account the event originated from; null for platform-stream events. */
    private String stripeAccountId;

    /** Id of the Stripe object the event is about; null when the payload names none. */
    private String relatedObjectId;

    private Date created;

}
