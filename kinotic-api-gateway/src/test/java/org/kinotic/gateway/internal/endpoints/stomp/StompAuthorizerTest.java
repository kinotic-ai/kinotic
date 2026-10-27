package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.security.ConnectedInfo;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies what the gateway layers on top of a participant's zone rules: the rules the server's resolver derives,
 * narrowed to the server's zone partitioning, reply destinations scoped to the connection, and one-shot send grants.
 */
public class StompAuthorizerTest {

    private static final String REPLY_TO_ID = "reply-to-1";
    private static final String REPLY_DESTINATION = "reply://" + REPLY_TO_ID + ":sub-1@kinotic.js.EventBus/replyHandler";

    private StompAuthorizer authorizer(ZonePartitioningService partitioning) {
        StompAuthorizerFactory factory = new StompAuthorizerFactory(partitioning,
                                                                    participant -> Future.succeededFuture(ZoneRules.of(Set.of("app.acme-org", "shared-api"),
                                                                                                                       Set.of("app.acme-org"))));
        return factory.create(new ConnectedInfo(new TestParticipant("user"), REPLY_TO_ID, null)).result();
    }

    @Test
    public void zoneChecksFollowTheResolvedRules() {
        StompAuthorizer authorizer = authorizer(ZonePartitioningService.everyZone("test"));

        assertTrue(authorizer.sendAllowed(CRI.create("srv://shared-api~SharedService/call#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));

        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.other-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://shared-api~SharedService#1.0.0")));
    }

    @Test
    public void theResolvedRulesAreNarrowedToWhatTheServerServes() {
        StompAuthorizer authorizer = authorizer(ZonePartitioningService.of("shared", Set.of("shared-api"), Set.of("shared-api")));

        assertTrue(authorizer.sendAllowed(CRI.create("srv://shared-api~SharedService/call#1.0.0")));
        // the participant may address its application zones, but this server neither reaches nor hosts them
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        // reply destinations carry no zone and pass the partitioning
        assertTrue(authorizer.subscribeAllowed(CRI.create(REPLY_DESTINATION)));
    }

    @Test
    public void replyDestinationIsScopedToTheConnectionsReplyToId() {
        StompAuthorizer authorizer = authorizer(ZonePartitioningService.everyZone("test"));

        assertTrue(authorizer.subscribeAllowed(CRI.create(REPLY_DESTINATION)));
        assertFalse(authorizer.subscribeAllowed(CRI.create("reply://other-id:sub-1@kinotic.js.EventBus/replyHandler")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("reply://kinotic.js.EventBus/replyHandler")));
    }

    @Test
    public void temporaryGrantsAllowOneExactMatchSend() {
        StompAuthorizer authorizer = authorizer(ZonePartitioningService.everyZone("test"));

        assertFalse(authorizer.sendAllowed(CRI.create(REPLY_DESTINATION)));

        // grants match by exact string, so a case variant of the granted destination stays denied
        authorizer.addTemporarySendAllowed(REPLY_DESTINATION);
        assertFalse(authorizer.sendAllowed(CRI.create("reply://" + REPLY_TO_ID + ":sub-1@Kinotic.js.EventBus/replyHandler")));
        assertTrue(authorizer.sendAllowed(CRI.create(REPLY_DESTINATION)));

        // a grant authorizes exactly one send
        assertFalse(authorizer.sendAllowed(CRI.create(REPLY_DESTINATION)));
    }

}
