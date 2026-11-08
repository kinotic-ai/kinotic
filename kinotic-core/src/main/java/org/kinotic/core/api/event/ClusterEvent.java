package org.kinotic.core.api.event;

import java.io.Serializable;
import java.util.Set;

/**
 * An event a {@link ClusterEventPublisher} delivers to the {@link ClusterEventHandler}s of its type on the nodes of the
 * servers it names.
 * It crosses nodes, so it is serializable and holds nothing but data.
 */
public interface ClusterEvent extends Serializable {

    /**
     * The names of the Ignite node attributes a node carries to be delivered the event, whatever their values; empty
     * for every node.
     */
    Set<String> nodeAttributeFilter();

}
