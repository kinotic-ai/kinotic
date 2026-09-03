package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Scope;
import reactor.core.publisher.Mono;

/**
 * Proxy making scoped invocations of the {@link ScopedRpcTestService}.
 */
@Proxy(namespace = "org.kinotic.core.internal.api.support",
       name = "ScopedRpcTestService")
public interface ScopedRpcTestServiceProxy {

    Mono<String> anyInstanceValue(@Scope String scope);

    Mono<String> instanceValue(@Scope String scope);

}
