package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
public class DefaultTestRenamedService implements TestRenamedService {

    @Override
    @McpTool(description = "Greets the recipient")
    public CompletableFuture<String> greet(String name) {
        return CompletableFuture.completedFuture("Hello " + name);
    }

}
