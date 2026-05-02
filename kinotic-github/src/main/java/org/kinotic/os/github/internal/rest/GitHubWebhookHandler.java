package org.kinotic.os.github.internal.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.kinotic.os.github.api.model.GitHubWebhookEvent;
import org.kinotic.os.github.api.rest.GithubConstants;
import org.kinotic.os.github.api.services.GitHubWebhookEventService;
import org.kinotic.os.github.internal.security.GitHubWebhookVerifier;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/github/webhook}: HMAC-verifies the delivery, parses the JSON, and
 * hands a {@link GitHubWebhookEvent} to {@link GitHubWebhookEventService}.
 * <p>
 * The {@link BodyHandler} is route-scoped (not global) with a 25 MiB cap matching
 * GitHub's documented webhook ceiling; oversized payloads are rejected with 413
 * before HMAC ever runs. HMAC verification is computed against the raw bytes
 * <strong>before</strong> any JSON decode — see {@link GitHubWebhookVerifier} —
 * since whitespace differences would invalidate the signature.
 * <p>
 * Always returns 204 quickly so GitHub doesn't redeliver. Any internal failure is
 * logged and dropped by the dispatch service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubWebhookHandler {

    private static final String HEADER_EVENT = "X-GitHub-Event";
    private static final String HEADER_DELIVERY = "X-GitHub-Delivery";
    private static final String HEADER_SIGNATURE = "X-Hub-Signature-256";

    /** GitHub's documented webhook ceiling — anything larger is rejected with 413. */
    private static final long WEBHOOK_BODY_LIMIT_BYTES = 25L * 1024 * 1024;

    private final Vertx vertx;
    private final KinoticGithubProperties properties;
    private final GitHubWebhookEventService webhookEventService;

    public void mountRoute(Router router) {
        router.post(GithubConstants.WEBHOOK_PATH)
              .handler(BodyHandler.create().setBodyLimit(WEBHOOK_BODY_LIMIT_BYTES))
              .handler(this::handleWebhook);
    }

    private void handleWebhook(RoutingContext ctx) {
        String eventType = ctx.request().getHeader(HEADER_EVENT);
        String deliveryId = ctx.request().getHeader(HEADER_DELIVERY);
        String signature = ctx.request().getHeader(HEADER_SIGNATURE);

        if (eventType == null) {
            ctx.response().setStatusCode(400).end();
            return;
        }
        Buffer body = ctx.body().buffer();
        if (body == null) {
            ctx.response().setStatusCode(400).end();
            return;
        }

        // HMAC verification runs in executeBlocking so a 25 MiB payload doesn't pin
        // the event loop. ordered=false lets concurrent webhooks run in parallel.
        byte[] bodyBytes = body.getBytes();
        String secret = properties.getGithub().getWebhookSecret();
        Future<Boolean> verify = vertx.executeBlocking(
                () -> GitHubWebhookVerifier.verify(bodyBytes, secret, signature),
                false);
        verify.onComplete(ar -> {
            if (ar.failed() || !Boolean.TRUE.equals(ar.result())) {
                log.warn("Rejecting webhook {} {} — signature mismatch",
                         eventType, deliveryId);
                ctx.response().setStatusCode(401).end();
                return;
            }
            JsonObject payload;
            try {
                payload = body.toJsonObject();
            } catch (Exception e) {
                ctx.response().setStatusCode(400).end();
                return;
            }
            GitHubWebhookEvent event = buildEvent(eventType, deliveryId, payload);
            // Ack first; processing is best-effort. GitHub's redelivery logic is based on
            // the HTTP response, not the downstream outcome.
            ctx.response().setStatusCode(204).end();
            webhookEventService.process(event).whenComplete((v, err) -> {
                if (err != null) {
                    log.warn("Webhook processing failed for {} {}: {}",
                             eventType, deliveryId, err.getMessage());
                }
            });
        });
    }

    private static GitHubWebhookEvent buildEvent(String eventType,
                                                 String deliveryId,
                                                 JsonObject payload) {
        JsonObject install = payload.getJsonObject("installation");
        String installationId = install != null && install.getLong("id") != null
                ? String.valueOf(install.getLong("id")) : null;
        JsonObject repo = payload.getJsonObject("repository");
        String repoFullName = repo != null ? repo.getString("full_name") : null;
        return new GitHubWebhookEvent()
                .setEventType(eventType)
                .setDeliveryId(deliveryId)
                .setInstallationId(installationId)
                .setRepoFullName(repoFullName)
                .setPayload(payload);
    }
}
