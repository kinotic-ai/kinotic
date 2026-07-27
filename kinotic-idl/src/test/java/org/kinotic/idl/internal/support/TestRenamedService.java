package org.kinotic.idl.internal.support;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
public interface TestRenamedService {

    CompletableFuture<String> greet(String recipientName);

}
