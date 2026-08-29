package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Proxy;
import reactor.core.publisher.Mono;

/**
 * Proxy making unscoped invocations of the {@link ScopedRpcTestService}, targeting its shared
 * unscoped address.
 */
@Proxy(namespace = "org.kinotic.core.internal.api.support",
       name = "ScopedRpcTestService")
public interface ScopedRpcTestServiceUnscopedProxy {

    Mono<String> anyInstanceValue();

    Mono<String> instanceValue();

}
