package org.kinotic.gateway.mcp;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TestStreamingService implements ITestStreamingService {

    @Override
    public Flux<String> ticker() {
        return Flux.just("tick");
    }
}
