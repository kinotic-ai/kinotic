package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the polymorphic wire and index shape of {@link ParticipantIdentity}: the {@code type}
 * discriminator each document carries, and resolution back to the concrete subtype when
 * deserializing through the base class — the shape every repository read depends on.
 */
class ParticipantIdentitySerializationTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void userRoundTripsThroughBaseClassWithTypeDiscriminator() {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("jane@example.com")
            .setDisplayName("Jane")
            .setAuthType(AuthType.LOCAL)
            .setOrganizationId("acme")
            .setId("user-1");

        String json = jsonMapper.writeValueAsString(user);
        assertTrue(json.contains("\"type\":\"USER\""), "document must carry the discriminator: " + json);

        ParticipantIdentity read = jsonMapper.readValue(json, ParticipantIdentity.class);
        UserParticipantIdentity readUser = assertInstanceOf(UserParticipantIdentity.class, read);
        assertEquals("jane@example.com", readUser.getEmail());
        assertEquals("acme", readUser.getOrganizationId());
    }

    @Test
    void delegateRoundTripsThroughBaseClassWithTypeDiscriminator() {
        DelegatingParticipantIdentity delegate = new DelegatingParticipantIdentity();
        delegate.setOwnerId("user-1")
                .setClientKey("https://claude.ai/oauth/claude-code-client-metadata")
                .setDelegateKind(DelegateKind.MCP_CLIENT)
                .setDisplayName("Claude Code")
                .setAuthType(AuthType.DELEGATED)
                .setOrganizationId("acme")
                .setId("delegate-1");

        String json = jsonMapper.writeValueAsString(delegate);
        assertTrue(json.contains("\"type\":\"DELEGATE\""), "document must carry the discriminator: " + json);

        ParticipantIdentity read = jsonMapper.readValue(json, ParticipantIdentity.class);
        DelegatingParticipantIdentity readDelegate = assertInstanceOf(DelegatingParticipantIdentity.class, read);
        assertEquals("user-1", readDelegate.getOwnerId());
        assertEquals(DelegateKind.MCP_CLIENT, readDelegate.getDelegateKind());
    }
}
