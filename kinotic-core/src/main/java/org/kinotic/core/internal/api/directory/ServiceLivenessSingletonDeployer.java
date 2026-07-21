package org.kinotic.core.internal.api.directory;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.directory.DefaultServiceDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deploys the {@link ServiceLivenessUpdater} as one HA cluster singleton on the Ignite service grid. Every node
 * requests the deployment on startup; Ignite elects a single host for it cluster-wide.
 */
@Slf4j
@Component
public class ServiceLivenessSingletonDeployer {

    private static final String SINGLETON_NAME = "kinotic-service-liveness-updater";

    private final Ignite ignite;
    private final ObjectProvider<DefaultServiceDirectory> directoryProvider;

    public ServiceLivenessSingletonDeployer(@Autowired(required = false) Ignite ignite,
                                            ObjectProvider<DefaultServiceDirectory> directoryProvider) {
        this.ignite = ignite;
        this.directoryProvider = directoryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void deploy() {
        // with no directory there is no liveness to maintain, and nothing is deployed
        if (directoryProvider.getIfAvailable() == null) {
            return;
        }
        if (ignite == null) {
            log.warn("Ignite is not available; the service liveness updater singleton will not be deployed");
            return;
        }
        // ServiceLivenessUpdater is an Ignite Service; its Spring dependencies are injected via
        // @SpringResource on the node Ignite elects to host it
        ignite.services().deployClusterSingleton(SINGLETON_NAME, new ServiceLivenessUpdater());
    }

}
