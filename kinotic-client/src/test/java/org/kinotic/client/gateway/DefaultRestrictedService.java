package org.kinotic.client.gateway;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Component
public class DefaultRestrictedService implements RestrictedService {

    @Override
    public CompletableFuture<String> secret() {
        return CompletableFuture.completedFuture("classified");
    }

}
