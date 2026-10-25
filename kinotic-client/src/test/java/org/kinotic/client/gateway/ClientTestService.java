package org.kinotic.client.gateway;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * The service the client tests call, one method per supported return type.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Publish
@Version("1.0.0")
@Zone(TestGatewayApplication.ZONE)
public interface ClientTestService {

    CompletableFuture<String> greet(String name);

    Future<Integer> add(int left, int right);

    Mono<TestDto> describe(TestDto dto);

    Flux<Integer> count(int upTo);

    /**
     * An endless stream, one value every 50 ms, ended only by the caller's cancellation.
     */
    Flux<Long> ticks();

    /**
     * Fails with an {@link IllegalStateException} carrying the message.
     */
    CompletableFuture<String> failWith(String message);

    /**
     * Never completes.
     */
    CompletableFuture<String> never();

}
