package org.kinotic.domain.internal.api.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.internal.api.aignite.IgniteServiceAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deploys the {@link ServiceLivenessUpdater} as one HA cluster singleton on the Ignite service grid. Every domain node
 * requests the deployment on startup; Ignite elects a single host for it cluster-wide.
 */
@Slf4j
@Component
public class ServiceLivenessSingletonDeployer {

    private static final String SINGLETON_NAME = "kinotic-service-liveness-updater";

    private final Ignite ignite;

    public ServiceLivenessSingletonDeployer(@Autowired(required = false) Ignite ignite) {
        this.ignite = ignite;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void deploy() {
        if (ignite == null) {
            log.warn("Ignite is not available; the service liveness updater singleton will not be deployed");
            return;
        }
        ignite.services().deployClusterSingleton(SINGLETON_NAME,
                new IgniteServiceAdapter(SINGLETON_NAME, ServiceLivenessUpdater.class, new Object[0]));
    }

}
