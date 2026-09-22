package org.kinotic.auth.engines;

import java.util.Map;

/**
 * The shape of a policy compiled to a JVM class: a boolean over the request map holding
 * {@code sub} (participant attributes) and {@code obj} (named method arguments).
 */
public interface PolicyPredicate {

    boolean test(Map<String, Object> r);
}
