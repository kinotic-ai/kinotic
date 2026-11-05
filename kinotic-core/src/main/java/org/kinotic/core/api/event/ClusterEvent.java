package org.kinotic.core.api.event;

import org.kinotic.core.api.ServerType;

import java.io.Serializable;
import java.util.Set;

/**
 * An application event a {@link ClusterEventPublisher} delivers to other nodes, which names the servers it is for.
 * It crosses nodes, so it is serializable and holds nothing but data.
 */
public interface ClusterEvent extends Serializable {

    /**
     * The {@link ServerType}s whose nodes the event is delivered to; empty for every server node.
     */
    Set<ServerType> serverTypeFilter();

}
