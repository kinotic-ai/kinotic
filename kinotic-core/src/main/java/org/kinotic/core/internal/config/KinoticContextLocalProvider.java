package org.kinotic.core.internal.config;

import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;
import org.kinotic.core.api.security.Participant;

/**
 * Registers Vert.x {@link ContextLocal} storage keys before the Vertx instance is created.
 * Discovered via SPI in META-INF/services/io.vertx.core.spi.VertxServiceProvider.
 *
 * Created by Claude on 2026-04-11.
 */
public class KinoticContextLocalProvider implements VertxServiceProvider {

    public static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);

    @Override
    public void init(VertxBootstrap vertxBootstrap) {
        // no-op, registration happens in the static field above
    }

}
