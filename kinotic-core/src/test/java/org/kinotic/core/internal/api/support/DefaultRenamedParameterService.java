package org.kinotic.core.internal.api.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@Component
public class DefaultRenamedParameterService implements RenamedParameterService {

    @Override
    public CompletableFuture<String> echo(String implementationName) {
        return CompletableFuture.completedFuture(implementationName);
    }

}
