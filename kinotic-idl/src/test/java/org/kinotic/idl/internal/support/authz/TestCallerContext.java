package org.kinotic.idl.internal.support.authz;

/**
 * A parameter type the test context declares as supplied by the platform, so a function taking it leaves it
 * out of its contract.
 */
public interface TestCallerContext {
    String getId();
}
