package org.kinotic.domain.internal.api.ee.license;

import org.kinotic.core.api.event.ClusterEvent;

import java.io.Serial;
import java.util.Set;

/**
 * Says a new Kinotic Enterprise License key was installed, so each node running kinotic-domain reads it.
 */
public class LicenseChangedEvent implements ClusterEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    // advertised by every node running kinotic-domain, from the module's META-INF/kinotic-domain.properties
    private static final String DOMAIN_NODE_ATTRIBUTE = "kinotic.domain";

    @Override
    public Set<String> nodeAttributeFilter() {
        return Set.of(DOMAIN_NODE_ATTRIBUTE);
    }
}
