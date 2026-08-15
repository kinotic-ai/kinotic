package org.kinotic.idl.internal.support;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes two instantiations of the same generic container in one contract.
 */
public interface TestPagedService {

    CompletableFuture<TestPage<TestObject>> findObjects();

    TestPage<TestAddress> findAddresses();

}
