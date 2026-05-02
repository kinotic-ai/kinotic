package org.kinotic.github.internal.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.api.rest.GithubConstants;
import org.kinotic.github.api.services.GitHubWebhookEventService;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code POST /api/github/webhook}: HMAC-verifies the delivery, parses the JSON, and
 * hands a {@link GitHubWebhookEvent} to {@link GitHubWebhookEventService}.
 * <p>
 * The {@link BodyHandler} is route-scoped (not global) with a 25 MiB cap matching
 * GitHub's documented webhook ceiling; oversized payloads are rejected with 413
 * before HMAC ever runs. HMAC verification is computed against the raw bytes
 * <strong>before</strong> any JSON decode — whitespace differences would invalidate
 * the signature. Compare is constant-time so the expected digest doesn't leak
 * through timing.
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
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

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
                () -> verifySignature(bodyBytes, secret, signature),
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

    /**
     * HMAC-SHA256 the raw body with the App's webhook secret and compare (constant-time)
     * against the {@code sha256=<hex>} signature GitHub sent in the header.
     */
    private static boolean verifySignature(byte[] rawBody, String secret, String headerSignature) {
        if (rawBody == null || secret == null || secret.isEmpty()
                || headerSignature == null || !headerSignature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        String expectedHex;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            expectedHex = toHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            return false;
        }
        String received = headerSignature.substring(SIGNATURE_PREFIX.length());
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                received.getBytes(StandardCharsets.US_ASCII));
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private static GitHubWebhookEvent buildEvent(String eventType,
                                                 String deliveryId,
                                                 JsonObject payload) {
        JsonObject install = payload.getJsonObject("installation");
        Long installationId = install != null ? install.getLong("id") : null;
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
