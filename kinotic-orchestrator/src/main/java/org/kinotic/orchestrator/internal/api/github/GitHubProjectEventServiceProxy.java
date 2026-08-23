package org.kinotic.orchestrator.internal.api.github;

import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import reactor.core.publisher.Flux;

/**
 * Proxy to the {@code GitHubProjectEventService} published by the GitHub module, addressed
 * by name because the orchestrator cannot compile against kinotic-github. The zone and
 * version must match the published service's declarations (its package-info carries
 * {@code @Zone(OS_API_ZONE)} and {@code @Version("1.0.0")}), or the proxy targets an
 * address nothing listens on.
 * <p>
 * The subscription is authorized by the participant bound to the calling Vert.x context —
 * the callee streams unfiltered only to a system participant, so the subscriber must bind
 * one before invoking {@link #events()}.
 */
@Proxy(namespace = "org.kinotic.github.api.services", name = "GitHubProjectEventService")
@Zone(DomainUtil.OS_API_ZONE)
@Version("1.0.0")
public interface GitHubProjectEventServiceProxy {

    /**
     * Opens the live stream of GitHub deliveries for repositories backing Kinotic Projects.
     * Best-effort delivery: only deliveries arriving while subscribed are seen, and GitHub
     * may redeliver — consumers must be idempotent.
     */
    Flux<GitHubProjectEvent> events();

}
