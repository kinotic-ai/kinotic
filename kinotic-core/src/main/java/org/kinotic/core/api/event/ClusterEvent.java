package org.kinotic.core.api.event;

import java.io.Serializable;
import java.util.Set;

/**
 * An application event a {@link ClusterEventPublisher} delivers to other nodes, which names the servers it is for.
 * It crosses nodes, so it is serializable and holds nothing but data.
 */
public interface ClusterEvent extends Serializable {

    /**
     * The names of the Ignite node attributes a node carries to be delivered the event, whatever their values; empty
     * for every node.
     */
    Set<String> nodeAttributeFilter();

}
