package org.kinotic.client.gateway;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Component
public class DefaultClientTestService implements ClientTestService {

    private final AtomicInteger activeTicks = new AtomicInteger();

    @Override
    public CompletableFuture<String> greet(String name) {
        return CompletableFuture.completedFuture("Hello " + name);
    }

    @Override
    public Future<Integer> add(int left, int right) {
        return Future.succeededFuture(left + right);
    }

    @Override
    public Mono<TestDto> describe(TestDto dto) {
        return Mono.just(dto.setDescription(dto.getCount() + " x " + dto.getName()));
    }

    @Override
    public Flux<Integer> count(int upTo) {
        return Flux.range(1, upTo);
    }

    @Override
    public Flux<Long> ticks() {
        return Flux.interval(Duration.ofMillis(50))
                   .doOnSubscribe(subscription -> activeTicks.incrementAndGet())
                   .doFinally(signal -> activeTicks.decrementAndGet());
    }

    @Override
    public CompletableFuture<String> failWith(String message) {
        throw new IllegalStateException(message);
    }

    @Override
    public CompletableFuture<String> never() {
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Shape> shape(double radius) {
        return CompletableFuture.completedFuture(new Circle().setRadius(radius));
    }

    /**
     * @return the number of {@link #ticks()} streams still running
     */
    public int activeTicks() {
        return activeTicks.get();
    }

}
