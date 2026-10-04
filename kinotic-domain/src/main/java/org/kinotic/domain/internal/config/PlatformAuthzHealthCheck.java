package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.springframework.stereotype.Component;

/**
 * Registers the {@code platform-authorization} procedure of the server's {@link HealthChecks}, which reports the
 * server able to authorize requests once the platform store runs a model. Until then every request a member
 * makes is refused, so a server is not ready to serve before it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAuthzHealthCheck {

    private static final long POLL_MILLIS = 2_000;

    private final AuthzStoreService stores;
    private final HealthChecks healthChecks;
    private final Vertx vertx;
    private volatile boolean ready;

    @PostConstruct
    void init() {
        healthChecks.register("platform-authorization", future -> {
            if (ready) {
                future.complete(Status.OK());
            } else {
                future.fail("The platform store runs no model yet");
            }
        });
        vertx.runOnContext(v -> poll());
    }

    // The model appears once the reconciler has written it, which may be after this server started; the poll ends there
    private void poll() {
        stores.platformModelId()
              .onSuccess(modelId -> {
                  log.info("The platform store runs model {}; requests are authorized against it", modelId);
                  ready = true;
              })
              .onFailure(e -> {
                  log.debug("The platform store runs no model yet: {}", e.getMessage());
                  vertx.setTimer(POLL_MILLIS, t -> poll());
              });
    }
}
