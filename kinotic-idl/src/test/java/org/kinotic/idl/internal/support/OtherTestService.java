package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpToolInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/21/26.
 */
public interface OtherTestService {

    @McpToolInfo(readOnlyHint = true)
    TestObject findPerson(String name);

    CompletableFuture<TestObject> findPersonAsync(String name);

    Flux<TestObject> streamPeople();

    TestAddress findAddress(TestObject person);

    Mono<TestAddress> findAddressAsync(TestObject person);

}
