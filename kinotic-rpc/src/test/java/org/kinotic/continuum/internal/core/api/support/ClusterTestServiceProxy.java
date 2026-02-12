

package org.kinotic.continuum.internal.core.api.support;

import org.kinotic.rpc.api.annotations.Proxy;
import io.vertx.core.Future;
import reactor.core.publisher.Mono;

/**
 *
 * Created by navid on 10/17/19
 */
@Proxy(namespace = "org.kinotic.continuum.internal.core.api.support",
       name="ClusterTestService")
public interface ClusterTestServiceProxy {

    Mono<Long> getFreeMemory();

    Future<String> getData();

}
