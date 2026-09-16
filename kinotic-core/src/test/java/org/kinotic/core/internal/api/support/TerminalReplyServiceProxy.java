package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Proxy;
import reactor.core.publisher.Flux;

/**
 * Proxy for a service the tests answer with raw events, so reply shapes no Java supervisor
 * produces (a single-value runtime replying to a stream-typed caller) can be exercised.
 *
 * Created by Navíd Mitchell 🤪 on 8/27/26.
 */
@Proxy(namespace = "org.kinotic.core.internal.api.support",
       name = "TerminalReplyService")
public interface TerminalReplyServiceProxy {

    Flux<String> streamFromSingleValueRuntime();

}
