package org.kinotic.gateway.mcp;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TestAppEchoService implements ITestAppEchoService {

    @Override
    public CompletableFuture<String> echo(String message) {
        return CompletableFuture.completedFuture(message);
    }
}
