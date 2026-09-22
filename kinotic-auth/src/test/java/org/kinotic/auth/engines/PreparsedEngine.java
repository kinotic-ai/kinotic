package org.kinotic.auth.engines;

import java.util.Map;

/**
 * Benchmark entry point that evaluates a registered policy against already-parsed inputs — the
 * participant attributes and the named method arguments — so evaluator cost can be measured
 * without the per-request JSON parse every {@code AuthorizationEngine} performs.
 */
public interface PreparsedEngine {

    boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments);
}
