package org.kinotic.billing.internal.endpoints;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StripeWebhookSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret";
    private static final Instant NOW = Instant.ofEpochSecond(1_770_000_000L);

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final StripeWebhookSignatureVerifier verifier = new StripeWebhookSignatureVerifier(fixedClock);

    /** Forges a valid Stripe-Signature header the way the SDK's own tests do. */
    private String signatureHeader(String payload, String secret, long timestamp) throws Exception {
        String signature = Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
        return "t=" + timestamp + ",v1=" + signature;
    }

    private static final String SNAPSHOT_PAYLOAD = """
            {"id":"evt_snap","type":"charge.succeeded","livemode":false,"account":"acct_456",
             "created":1770000000,"data":{"object":{"id":"ch_888","object":"charge"}}}""";

    private static final String THIN_PAYLOAD = """
            {"id":"evt_thin","type":"charge.succeeded","livemode":true,"context":"acct_123",
             "created":"2026-02-01T12:00:00Z","related_object":{"id":"ch_777","type":"charge"}}""";

    @Test
    public void whenSignatureValid_thenEventParsed() throws Exception {
        ParsedStripeEvent event = verifier.verifyAndParse(
                SNAPSHOT_PAYLOAD,
                signatureHeader(SNAPSHOT_PAYLOAD, SECRET, NOW.getEpochSecond()),
                SECRET);
        assertEquals("evt_snap", event.getId());
        assertEquals("charge.succeeded", event.getType());
    }

    @Test
    public void whenSignatureForgedWithWrongSecret_thenThrows() throws Exception {
        String header = signatureHeader(SNAPSHOT_PAYLOAD, "whsec_wrong_secret", NOW.getEpochSecond());
        assertThrows(SignatureVerificationException.class,
                     () -> verifier.verifyAndParse(SNAPSHOT_PAYLOAD, header, SECRET));
    }

    @Test
    public void whenBodyTamperedAfterSigning_thenThrows() throws Exception {
        String header = signatureHeader(SNAPSHOT_PAYLOAD, SECRET, NOW.getEpochSecond());
        String tampered = SNAPSHOT_PAYLOAD.replace("ch_888", "ch_999");
        assertThrows(SignatureVerificationException.class,
                     () -> verifier.verifyAndParse(tampered, header, SECRET));
    }

    @Test
    public void whenTimestampOutsideTolerance_thenThrows() throws Exception {
        long staleTimestamp = NOW.getEpochSecond() - Webhook.DEFAULT_TOLERANCE - 100;
        String header = signatureHeader(SNAPSHOT_PAYLOAD, SECRET, staleTimestamp);
        assertThrows(SignatureVerificationException.class,
                     () -> verifier.verifyAndParse(SNAPSHOT_PAYLOAD, header, SECRET));
    }

    @Test
    public void whenSignatureHeaderMissing_thenThrows() {
        assertThrows(SignatureVerificationException.class,
                     () -> verifier.verifyAndParse(SNAPSHOT_PAYLOAD, null, SECRET));
    }

    @Test
    public void whenSnapshotEventPayload_thenDataObjectIdAndAccountExtracted() throws Exception {
        ParsedStripeEvent event = verifier.verifyAndParse(
                SNAPSHOT_PAYLOAD,
                signatureHeader(SNAPSHOT_PAYLOAD, SECRET, NOW.getEpochSecond()),
                SECRET);
        assertEquals("ch_888", event.getRelatedObjectId());
        assertEquals("acct_456", event.getStripeAccountId());
        assertEquals(Boolean.FALSE, event.getLivemode());
        assertEquals(new Date(1_770_000_000L * 1000), event.getCreated());
    }

    @Test
    public void whenThinEventPayload_thenRelatedObjectIdAndContextExtracted() throws Exception {
        ParsedStripeEvent event = verifier.verifyAndParse(
                THIN_PAYLOAD,
                signatureHeader(THIN_PAYLOAD, SECRET, NOW.getEpochSecond()),
                SECRET);
        assertEquals("ch_777", event.getRelatedObjectId());
        assertEquals("acct_123", event.getStripeAccountId());
        assertEquals(Boolean.TRUE, event.getLivemode());
        assertEquals(Date.from(Instant.parse("2026-02-01T12:00:00Z")), event.getCreated());
    }

    @Test
    public void whenPayloadNamesNoObject_thenRelatedObjectIdNull() throws Exception {
        String payload = "{\"id\":\"evt_x\",\"type\":\"balance.available\",\"livemode\":false}";
        ParsedStripeEvent event = verifier.verifyAndParse(
                payload,
                signatureHeader(payload, SECRET, NOW.getEpochSecond()),
                SECRET);
        assertNull(event.getRelatedObjectId());
        assertNull(event.getStripeAccountId());
        assertNull(event.getCreated());
    }
}
