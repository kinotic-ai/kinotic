package org.kinotic.gateway.mcp;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TestCalculatorService implements ITestCalculatorService {

    @Override
    public CompletableFuture<Integer> add(int left, int right) {
        return CompletableFuture.completedFuture(left + right);
    }

    @Override
    public CompletableFuture<String> join(String first, String second) {
        return CompletableFuture.completedFuture(first + " " + second);
    }
}
