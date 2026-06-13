

package org.kinotic.core.internal.api.event;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultEventBusService} local-delivery preference.
 * Uses a plain non-clustered Vert.x, so no Ignite is required.
 *
 * Created by Navid Mitchell on 2026-06-05.
 */
public class DefaultEventBusServiceTests {

    private Vertx vertx;
    private DefaultEventBusService eventBusService;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        eventBusService = new DefaultEventBusService(null, null, vertx);
    }

    @AfterEach
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void prefersLocalDeliveryWhenThisNodeHostsTheService() {
        Event<byte[]> request = serviceRequest("org.kinotic.tests.LocalPreferTestService");
        String baseResource = request.cri().baseResource();

        // No local handler yet, so the request should route normally.
        assertFalse(eventBusService.createDeliveryOptions(request, baseResource).isLocalOnly());

        EventConsumer consumer = eventBusService.listen(request.cri());

        // This node now hosts the service, so the request should be pinned local.
        assertTrue(eventBusService.createDeliveryOptions(request, baseResource).isLocalOnly());

        // A service this node does not host must still route normally.
        Event<byte[]> otherRequest = serviceRequest("org.kinotic.tests.OtherService");
        assertFalse(eventBusService.createDeliveryOptions(otherRequest, otherRequest.cri().baseResource()).isLocalOnly());

        // Once the local handler is unregistered, the preference is dropped again.
        consumer.unregister();
        assertFalse(eventBusService.createDeliveryOptions(request, baseResource).isLocalOnly());
    }

    @Test
    public void neverPinsNonServiceDestinationsLocal() {
        // Even though this node listens on the reply address, replies are never confined to local delivery.
        CRI replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME, "node1", "ReplyHandler");
        String baseResource = replyCri.baseResource();
        eventBusService.listen(replyCri);

        Event<byte[]> reply = Event.create(replyCri, Metadata.create(), new byte[0]);
        assertFalse(eventBusService.createDeliveryOptions(reply, baseResource).isLocalOnly());
    }

    private static Event<byte[]> serviceRequest(String serviceName) {
        CRI cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, null, serviceName, "/method", "1.0.0");
        return Event.create(cri, Metadata.create(), new byte[0]);
    }
}
