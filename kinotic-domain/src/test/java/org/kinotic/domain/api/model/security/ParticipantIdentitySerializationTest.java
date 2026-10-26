package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
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
    void eachKindOfUserRoundTripsThroughBaseClassWithItsOwnTypeDiscriminator() {
        SystemParticipantIdentity systemUser = new SystemParticipantIdentity();
        systemUser.setEmail("jane@example.com").setId("system-user-1");
        OrganizationParticipantIdentity organizationUser = new OrganizationParticipantIdentity();
        organizationUser.setOrganizationId("acme")
                        .setEmail("jane@example.com")
                        .setId("organization-user-1");
        ApplicationParticipantIdentity applicationUser = new ApplicationParticipantIdentity();
        applicationUser.setOrganizationId("acme")
                       .setApplicationId("orders")
                       .setEmail("jane@example.com")
                       .setId("application-user-1");

        assertRoundTrips(systemUser, "SYSTEM_USER", SystemParticipantIdentity.class);
        assertEquals("acme", assertRoundTrips(organizationUser, "ORGANIZATION_USER", OrganizationParticipantIdentity.class).getOrganizationId());
        ApplicationParticipantIdentity read = assertRoundTrips(applicationUser, "APPLICATION_USER", ApplicationParticipantIdentity.class);
        assertEquals("acme", read.getOrganizationId());
        assertEquals("orders", read.getApplicationId());
    }

    private <T extends UserParticipantIdentity> T assertRoundTrips(T user, String type, Class<T> kind) {
        String json = jsonMapper.writeValueAsString(user);
        assertTrue(json.contains("\"type\":\"" + type + "\""), "document must carry the discriminator: " + json);

        T read = assertInstanceOf(kind, jsonMapper.readValue(json, ParticipantIdentity.class));
        assertEquals("jane@example.com", read.getEmail());
        assertEquals(user.getId(), read.getId());
        return read;
    }

    @Test
    void delegateRoundTripsThroughBaseClassWithTypeDiscriminator() {
        DelegatingParticipantIdentity delegate = new DelegatingParticipantIdentity();
        delegate.setOwnerId("user-1")
                .setClientKey("https://claude.ai/oauth/claude-code-client-metadata")
                .setDelegateType(DelegateType.MCP_CLIENT)
                .setOrganizationId("acme")
                .setDisplayName("Claude Code")
                .setId("delegate-1");

        String json = jsonMapper.writeValueAsString(delegate);
        assertTrue(json.contains("\"type\":\"DELEGATE\""), "document must carry the discriminator: " + json);

        ParticipantIdentity read = jsonMapper.readValue(json, ParticipantIdentity.class);
        DelegatingParticipantIdentity readDelegate = assertInstanceOf(DelegatingParticipantIdentity.class, read);
        assertEquals("user-1", readDelegate.getOwnerId());
        assertEquals(DelegateType.MCP_CLIENT, readDelegate.getDelegateType());
    }
}
