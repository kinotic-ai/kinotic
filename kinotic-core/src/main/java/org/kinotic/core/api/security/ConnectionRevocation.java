package org.kinotic.core.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.ServerType;
import org.kinotic.core.api.event.ClusterEvent;

import java.util.Set;

/**
 * A revocation delivered to every server node, each of which runs a gateway: the connections of the named identity
 * or login end.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRevocation implements ClusterEvent {

    private ConnectionRevocationType type;
    private String id;

    @Override
    public Set<ServerType> serverTypeFilter() {
        return Set.of();
    }

}
