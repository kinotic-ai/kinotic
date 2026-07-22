package org.kinotic.idl.internal.support;

import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/21/26.
 */
public interface OtherTestService {

    TestObject findPerson(String name);

    CompletableFuture<TestObject> findPersonAsync(String name);

    Flux<TestObject> streamPeople();

    TestAddress findAddress(TestObject person);

}
