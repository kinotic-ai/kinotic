package org.kinotic.idl.internal.support;

/**
 * Publishes {@link TestPage} raw, leaving its type variable unresolved.
 */
public interface TestRawPageService {

    @SuppressWarnings("rawtypes")
    TestPage findRaw();

}
