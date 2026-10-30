package org.kinotic.client.gateway;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authenticates a connection from the {@code clientId} and {@code clientSecret} headers of its WebSocket upgrade:
 * the fixed test secret admits every client id as an ADMIN, anything else is rejected. While
 * {@link #rejectEveryone} is set, every connection is rejected, as a gateway whose security service is failing
 * rejects them; the rejections are counted.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class TestGatewaySecurityService implements SecurityService {

    // static, so a test sets it before the gateway it applies to starts
    public static volatile boolean rejectEveryone = false;
    public static final AtomicInteger rejections = new AtomicInteger();

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        Map<String, String> info = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (authenticationInfo != null) {
            info.putAll(authenticationInfo);
        }
        String clientId = info.get("clientId");
        Future<Participant> ret;
        if (!rejectEveryone && clientId != null && TestGatewayApplication.CLIENT_SECRET.equals(info.get("clientSecret"))) {
            ret = Future.succeededFuture(new TestParticipant(clientId, Map.of(), List.of("ADMIN")));
        } else {
            rejections.incrementAndGet();
            ret = Future.failedFuture(new AuthenticationException("Invalid client credentials"));
        }
        return ret;
    }

}
