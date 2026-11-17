package org.kinotic.test.support.billing;

import java.util.Map;

/**
 * A request the system server made to {@link StripeStandIn}: its method, path and form parameters, keyed as Stripe
 * encodes them, such as {@code line_items[0][price]}.
 */
public record StripeRequest(String method, String path, Map<String, String> params) {
}
