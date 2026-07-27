package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@McpTool(readOnlyHint = true)
public interface TestSweptService {

    CompletableFuture<TestObject> findByName(String name);

    @McpTool(description = "Counts every test object", title = "Count Objects")
    CompletableFuture<Long> countAll();

}
