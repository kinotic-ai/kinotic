package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * A bare {@code @McpTool} states no hints, so every function here serves what its name implies.
 */
@McpTool
public interface TestNamedHintService {

    CompletableFuture<Long> peopleCount();

    CompletableFuture<TestObject> getOrCreatePerson(String name);

    CompletableFuture<Void> purgeRetiredPeople();

    CompletableFuture<TestObject> createPersonIfNotExist(TestObject person);

    CompletableFuture<Void> notifyPeople();

}
