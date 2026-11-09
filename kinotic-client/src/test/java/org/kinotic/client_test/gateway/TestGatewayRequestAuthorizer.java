package org.kinotic.client_test.gateway;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.RequestAuthorizer;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link RequestAuthorizer} of the test gateway, which runs without the domain module and so without the
 * authorization engine. Every request the zone admits may proceed, so the client suite exercises the RPC
 * mechanism in isolation.
 */
public class TestGatewayRequestAuthorizer implements RequestAuthorizer {

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, ObjectNode arguments) {
        return Future.succeededFuture();
    }
}
