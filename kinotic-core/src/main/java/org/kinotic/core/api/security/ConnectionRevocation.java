package org.kinotic.core.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.KinoticNodeAttributes;
import org.kinotic.core.api.event.ClusterEvent;

import java.util.Set;

/**
 * A revocation delivered to every node running the API gateway: every connection authenticated as the identity
 * ends.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRevocation implements ClusterEvent {

    private String identityId;

    @Override
    public Set<String> nodeAttributeFilter() {
        return Set.of(KinoticNodeAttributes.API_GATEWAY);
    }

}
