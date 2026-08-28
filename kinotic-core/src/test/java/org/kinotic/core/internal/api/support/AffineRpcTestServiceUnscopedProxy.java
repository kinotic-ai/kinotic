package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Proxy;
import reactor.core.publisher.Mono;

/**
 * Proxy making unscoped invocations of the {@link AffineRpcTestService}, which listens on no
 * unscoped address.
 */
@Proxy(namespace = "org.kinotic.core.internal.api.support",
       name = "AffineRpcTestService")
public interface AffineRpcTestServiceUnscopedProxy {

    Mono<String> instanceValue();

}
