package org.kinotic.domain.api.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Immutable default implementation of {@link OrganizationParticipant}. Equality is by
 * {@code id} only, matching the long-standing {@link org.kinotic.core.api.security.Participant}
 * convention.
 */
@Getter
@EqualsAndHashCode(of = "id")
public class DefaultOrganizationParticipant implements OrganizationParticipant {

    private final String id;
    private final String organizationId;
    private final Map<String, String> metadata;
    private final List<String> roles;

    @Builder
    @JsonCreator
    public DefaultOrganizationParticipant(@JsonProperty("id") String id,
                                          @JsonProperty("organizationId") String organizationId,
                                          @JsonProperty("metadata") Map<String, String> metadata,
                                          @JsonProperty("roles") List<String> roles) {
        this.id = id;
        this.organizationId = organizationId;
        this.metadata = metadata;
        this.roles = roles;
    }
}
