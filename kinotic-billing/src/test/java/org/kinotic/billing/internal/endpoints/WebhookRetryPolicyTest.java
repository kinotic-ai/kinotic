package org.kinotic.billing.internal.endpoints;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebhookRetryPolicyTest {

    private final WebhookRetryPolicy policy = new WebhookRetryPolicy();

    @Test
    public void whenAttemptIncreases_thenDelayDoubles() {
        assertEquals(2_000, policy.delayMillis(1));
        assertEquals(4_000, policy.delayMillis(2));
        assertEquals(8_000, policy.delayMillis(3));
        assertEquals(16_000, policy.delayMillis(4));
    }

    @Test
    public void whenMaxAttemptsReached_thenExhausted() {
        assertFalse(policy.isExhausted(WebhookRetryPolicy.MAX_ATTEMPTS - 1));
        assertTrue(policy.isExhausted(WebhookRetryPolicy.MAX_ATTEMPTS));
    }
}
