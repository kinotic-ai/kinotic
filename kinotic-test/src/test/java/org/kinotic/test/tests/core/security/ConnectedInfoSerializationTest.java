package org.kinotic.test.tests.core.security;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;
import org.kinotic.domain.internal.config.KinoticDomainJacksonConfig;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the {@link io.vertx.core.shareddata.ClusterSerializable} round-trip the clustered
 * (Ignite) web-session store uses to marshal a {@link ConnectedInfo} between nodes, including its
 * polymorphic {@link Participant}.
 */
public class ConnectedInfoSerializationTest {

    @BeforeAll
    static void configureMapper() {
        // The same participant-aware module kinotic-domain registers on the application JsonMapper.
        JsonMapper mapper = JsonMapper.builder()
                                      .addModule(new KinoticDomainJacksonConfig().kinoticDomainModule())
                                      .build();
        ConnectedInfo.setSerializationMapper(mapper);
    }

    @Test
    void roundTripsApplicationParticipant() {
        DefaultApplicationParticipant participant = DefaultApplicationParticipant.builder()
                                                                                 .id("user-1")
                                                                                 .organizationId("org-1")
                                                                                 .applicationId("app-1")
                                                                                 .tenantId("tenant-1")
                                                                                 .metadata(Map.of("k", "v"))
                                                                                 .roles(List.of("ADMIN"))
                                                                                 .build();

        ConnectedInfo restored = roundTrip(new ConnectedInfo(participant, "reply-123"));

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
    void roundTripsSystemParticipantWithNullReplyToId() {
        DefaultSystemParticipant participant = DefaultSystemParticipant.builder()
                                                                       .id("node-1")
                                                                       .metadata(Map.of("type", "node"))
                                                                       .roles(List.of("NODE"))
                                                                       .build();

        ConnectedInfo restored = roundTrip(new ConnectedInfo(participant, null));

        assertNull(restored.getReplyToId());
        assertInstanceOf(DefaultSystemParticipant.class, restored.getParticipant());
        assertEquals("node-1", restored.getParticipant().getId());
    }

    private static ConnectedInfo roundTrip(ConnectedInfo original) {
        Buffer buffer = Buffer.buffer();
        original.writeToBuffer(buffer);

        ConnectedInfo restored = new ConnectedInfo();
        int read = restored.readFromBuffer(0, buffer);
        assertEquals(buffer.length(), read, "readFromBuffer must consume exactly what writeToBuffer wrote");
        return restored;
    }
}
