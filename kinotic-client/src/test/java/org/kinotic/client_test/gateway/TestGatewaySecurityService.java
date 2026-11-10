package org.kinotic.client_test.gateway;

import io.vertx.core.Future;
import org.kinotic.client.internal.api.ConnectedParticipant;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.AuthenticationContext;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authenticates a connection from the {@code clientId} and {@code clientSecret} headers of its WebSocket upgrade:
 * the fixed test secret admits every client id as an ADMIN, anything else is rejected. {@link #mode} makes it reject
 * every connection whatever its credentials, or fail every connection as a security service that cannot reach its
 * identity store does; the connections not admitted are counted in {@link #refusals}.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class TestGatewaySecurityService implements SecurityService {

    // static, so a test sets it before the gateway it applies to starts
    public static volatile TestSecurityServiceMode mode = TestSecurityServiceMode.AUTHENTICATE;
    public static final AtomicInteger refusals = new AtomicInteger();

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo, AuthenticationContext context) {
        // the gateway hands the upgrade headers over as a case-insensitive map
        String clientId = authenticationInfo.get("clientId");
        Future<Participant> ret;
        if (mode == TestSecurityServiceMode.FAIL) {
            refusals.incrementAndGet();
            ret = Future.failedFuture(new IllegalStateException("The identity store is unavailable"));
        } else if (mode == TestSecurityServiceMode.AUTHENTICATE
                && clientId != null
                && TestGatewayApplication.CLIENT_SECRET.equals(authenticationInfo.get("clientSecret"))) {
            ret = Future.succeededFuture(new ConnectedParticipant().setId(clientId).setMetadata(Map.of()).setRoles(List.of("ADMIN")));
        } else {
            refusals.incrementAndGet();
            ret = Future.failedFuture(new AuthenticationException("Invalid client credentials"));
        }
        return ret;
    }

}
