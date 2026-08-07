package org.kinotic.domain.internal.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kinotic.domain.api.model.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;

/**
 * Jackson mixin that turns the {@link org.kinotic.core.api.security.Participant} interface
 * polymorphic on a {@code "type"} discriminator. Kept out of the core interface to keep
 * RPC-only customers without {@code kinotic-domain} on the classpath unaffected.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DefaultSystemParticipant.class,       name = "system"),
        @JsonSubTypes.Type(value = DefaultOrganizationParticipant.class, name = "organization"),
        @JsonSubTypes.Type(value = DefaultApplicationParticipant.class,  name = "application")
})
abstract class ParticipantMixin {
}
