

package org.kinotic.rpc.gateway.api.security;

import org.kinotic.rpc.api.event.StreamData;
import org.kinotic.rpc.api.security.SessionMetadata;

import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 6/3/20
 */
//@Publish FIXME: add RBAC for this
//@Version("0.1.0")
public interface SessionInformationService {

    Flux<Long> countActiveSessionsContinuous();

    Flux<StreamData<String, SessionMetadata>> listActiveSessionsContinuous();

}
