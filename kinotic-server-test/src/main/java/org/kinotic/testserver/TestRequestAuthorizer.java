package org.kinotic.testserver;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link RequestAuthorizer} of the test server, which runs without the domain module's services and so
 * without the authorization engine. Every request the zone admits may proceed, so the
 * {@code @kinotic-ai/core} suite exercises the RPC mechanism in isolation.
 */
@Component
public class TestRequestAuthorizer implements RequestAuthorizer {

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, ObjectNode arguments) {
        return Future.succeededFuture();
    }
}
