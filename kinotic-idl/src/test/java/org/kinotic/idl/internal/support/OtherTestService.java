package org.kinotic.idl.internal.support;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/21/26.
 */
public interface OtherTestService {

    TestObject findPerson(String name);

    CompletableFuture<TestObject> findPersonAsync(String name);

    TestAddress findAddress(TestObject person);

}
