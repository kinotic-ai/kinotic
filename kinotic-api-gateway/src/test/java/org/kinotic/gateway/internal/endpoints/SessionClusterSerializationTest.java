package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.internal.config.KinoticDomainJacksonConfig;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the {@link io.vertx.core.shareddata.ClusterSerializable} round-trip used by the
 * clustered (Ignite) web-session store for the values the gateway stores in the session.
 */
class SessionClusterSerializationTest {

    @BeforeAll
    static void configureMapper() {
        // The same participant-aware module kinotic-domain registers on the application JsonMapper.
        JsonMapper mapper = JsonMapper.builder()
                                      .addModule(new KinoticDomainJacksonConfig().kinoticDomainModule())
                                      .build();
        ConnectedInfo.setSerializationMapper(mapper);
    }

    @Test
    void connectedInfoRoundTripsPolymorphicParticipant() {
        DefaultApplicationParticipant participant = DefaultApplicationParticipant.builder()
                                                                                 .id("user-1")
                                                                                 .organizationId("org-1")
                                                                                 .applicationId("app-1")
                                                                                 .tenantId("tenant-1")
                                                                                 .metadata(Map.of("k", "v"))
                                                                                 .roles(List.of("ADMIN"))
                                                                                 .build();
        ConnectedInfo original = new ConnectedInfo(participant, "reply-123");

        Buffer buffer = Buffer.buffer();
        original.writeToBuffer(buffer);

        ConnectedInfo restored = new ConnectedInfo();
        int read = restored.readFromBuffer(0, buffer);

        assertEquals(buffer.length(), read, "readFromBuffer must consume exactly what writeToBuffer wrote");
        assertEquals("reply-123", restored.getReplyToId());

        Participant restoredParticipant = restored.getParticipant();
        assertInstanceOf(DefaultApplicationParticipant.class, restoredParticipant,
                         "the polymorphic subtype must survive the round-trip");
        DefaultApplicationParticipant app = (DefaultApplicationParticipant) restoredParticipant;
        assertEquals("user-1", app.getId());
        assertEquals("org-1", app.getOrganizationId());
        assertEquals("app-1", app.getApplicationId());
        assertEquals("tenant-1", app.getTenantId());
        assertEquals(Map.of("k", "v"), app.getMetadata());
        assertEquals(List.of("ADMIN"), app.getRoles());
    }

    @Test
    void oidcFlowSessionRoundTrips() {
        OidcFlowSession original = new OidcFlowSession("state-1", "nonce-1", "verifier-1", "config-1", "org-1");

        Buffer buffer = Buffer.buffer();
        original.writeToBuffer(buffer);

        OidcFlowSession restored = new OidcFlowSession();
        restored.readFromBuffer(0, buffer);

        assertEquals("state-1", restored.state());
        assertEquals("nonce-1", restored.nonce());
        assertEquals("verifier-1", restored.pkceVerifier());
        assertEquals("config-1", restored.configId());
        assertEquals("org-1", restored.orgId());
    }

    @Test
    void oidcFlowSessionRoundTripsNullOrgId() {
        // Non-org-scoped flows stash a null orgId; the JsonObject encoding must preserve it.
        OidcFlowSession original = new OidcFlowSession("state-1", "nonce-1", "verifier-1", "config-1", null);

        Buffer buffer = Buffer.buffer();
        original.writeToBuffer(buffer);

        OidcFlowSession restored = new OidcFlowSession();
        restored.readFromBuffer(0, buffer);

        assertNull(restored.orgId());
        assertEquals("config-1", restored.configId());
    }
}
