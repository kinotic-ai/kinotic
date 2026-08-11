package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Idempotency and audit record for one Stripe webhook delivery. The {@code id} is the Stripe
 * event id, so a redelivered event maps onto the same record and is recognized as a duplicate.
 * <p>
 * Not organization-scoped: webhook deliveries arrive before any org attribution is known.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class StripeWebhookEvent implements Identifiable<String> {

    private String id;

    private String eventType;

    /** Connected account the event originated from; null for platform-stream events. */
    private String stripeAccountId;

    private Boolean livemode;

    /** Id of the Stripe object the event is about (charge, account, invoice, ...). */
    private String relatedObjectId;

    /** Raw request body as delivered, retained for dead-letter forensics and replay. */
    private String payload;

    private Date receivedAt;

    private Date processedAt;

    private StripeWebhookEventStatus status;

    private String lastError;

}
