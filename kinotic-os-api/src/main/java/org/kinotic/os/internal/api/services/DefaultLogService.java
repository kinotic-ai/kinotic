package org.kinotic.os.internal.api.services;

import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.os.api.model.LogQuery;
import org.kinotic.os.api.services.LogService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Default {@link LogService} that derives the Loki tenant from the authenticated participant's organization
 * and delegates to {@link LokiClient}.
 */
@Component
@RequiredArgsConstructor
public class DefaultLogService implements LogService {

    private final LokiClient lokiClient;
    private final SecurityContext securityContext;

    @Override
    public Flux<byte[]> tail(String query) {
        return lokiClient.tail(requireOrganizationId(), query);
    }

    @Override
    public CompletableFuture<byte[]> history(LogQuery query) {
        return lokiClient.queryRange(requireOrganizationId(), query)
                         .map(Buffer::getBytes)
                         .toCompletionStage()
                         .toCompletableFuture();
    }

    private String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }
}
