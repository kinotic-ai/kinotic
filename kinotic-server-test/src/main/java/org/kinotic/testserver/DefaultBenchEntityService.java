package org.kinotic.testserver;

import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultBenchEntityService implements IBenchEntityService {

    // About the size of a typical entity document, so the response body costs what a real read's does
    private static final String NOTES = "x".repeat(700);

    private final Vertx vertx;

    @Override
    public CompletableFuture<Map<String, Object>> findById(String id, int delayMs) {
        CompletableFuture<Map<String, Object>> ret = new CompletableFuture<>();
        if (delayMs > 0) {
            vertx.timer(delayMs).onComplete(_ -> ret.complete(entity(id)));
        } else {
            ret.complete(entity(id));
        }
        return ret;
    }

    private static Map<String, Object> entity(String id) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("id", id);
        ret.put("firstName", "Jane");
        ret.put("lastName", "Doe");
        ret.put("age", 28);
        ret.put("tags", List.of("bench", "fake", "persistence"));
        ret.put("address", Map.of("street", "1 Main St", "city", "Springfield", "zip", "12345"));
        ret.put("notes", NOTES);
        return ret;
    }
}
