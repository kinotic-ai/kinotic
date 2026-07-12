package org.kinotic.billing.internal.endpoints;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

/**
 * Verifies a Stripe webhook delivery against its {@code Stripe-Signature} header and
 * extracts the {@link ParsedStripeEvent} envelope.
 * <p>
 * The signature covers the exact raw body bytes — callers must pass the body untouched.
 */
@RequiredArgsConstructor
public class StripeWebhookSignatureVerifier {

    private final Clock clock;

    /**
     * @param payload         the raw request body, exactly as delivered
     * @param signatureHeader the {@code Stripe-Signature} header value
     * @param signingSecret   the endpoint's {@code whsec_...} secret
     * @return the parsed event envelope
     * @throws SignatureVerificationException if the signature is missing, malformed, forged,
     *                                        or outside the timestamp tolerance
     */
    public ParsedStripeEvent verifyAndParse(String payload,
                                            String signatureHeader,
                                            String signingSecret) throws SignatureVerificationException {
        // The SDK NPEs on a null header rather than rejecting it; guard so a missing header
        // is an ordinary verification failure, not a server error.
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new SignatureVerificationException("No Stripe-Signature header present", signatureHeader);
        }
        Webhook.Signature.verifyHeader(payload, signatureHeader, signingSecret,
                                       Webhook.DEFAULT_TOLERANCE, clock);
        return parse(payload);
    }

    private ParsedStripeEvent parse(String payload) {
        JsonObject json = new JsonObject(payload);
        String stripeAccountId = json.getString("account");
        if (stripeAccountId == null) {
            // v2 thin events carry the originating account in `context` instead of `account`
            stripeAccountId = json.getString("context");
        }
        return new ParsedStripeEvent()
                .setId(json.getString("id"))
                .setType(json.getString("type"))
                .setLivemode(json.getBoolean("livemode"))
                .setStripeAccountId(stripeAccountId)
                .setRelatedObjectId(extractRelatedObjectId(json));
    }

    private String extractRelatedObjectId(JsonObject json) {
        JsonObject relatedObject = json.getJsonObject("related_object");
        if (relatedObject != null) {
            return relatedObject.getString("id");
        }
        JsonObject data = json.getJsonObject("data");
        if (data != null) {
            JsonObject object = data.getJsonObject("object");
            if (object != null) {
                return object.getString("id");
            }
        }
        return null;
    }

}
