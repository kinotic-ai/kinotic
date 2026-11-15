package org.kinotic.test.support.billing;

import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stand-in for the parts of the Stripe API billing calls: a test reports a subscription as Stripe would have it,
 * every object created gets a fresh id, a request repeating an earlier one's idempotency key gets the earlier
 * response, and every request is recorded for a test to read. The system's
 * {@link StripeClient} reaches it through the same requests and parsing it makes against Stripe.
 */
@Configuration
public class StripeStandIn {

    private static final String SUBSCRIPTIONS_PATH = "/v1/subscriptions/";
    private static final String CHECKOUT_SESSIONS_PATH = "/v1/checkout/sessions";
    private static final String EXPIRE = "/expire";
    private static final Map<String, JsonObject> SUBSCRIPTIONS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> CHECKOUT_SESSIONS = new ConcurrentHashMap<>();
    private static final List<JsonObject> METERS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, JsonObject> IDEMPOTENT_RESPONSES = new ConcurrentHashMap<>();
    private static final List<StripeRequest> REQUESTS = Collections.synchronizedList(new ArrayList<>());

    /** Stripe has the subscription as given from now on. */
    public static JsonObject report(JsonObject subscription) {
        SUBSCRIPTIONS.put(subscription.getString("id"), subscription);
        return subscription;
    }

    /** The requests made with the method to a path starting with the prefix, oldest first. */
    public static List<StripeRequest> requests(String method, String pathPrefix) {
        synchronized (REQUESTS) {
            return REQUESTS.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).toList();
        }
    }

    /** The Checkout Sessions opened with the client reference id. */
    public static List<JsonObject> checkoutSessions(String clientReferenceId) {
        return CHECKOUT_SESSIONS.values().stream()
                                .filter(session -> clientReferenceId.equals(session.getString("client_reference_id")))
                                .toList();
    }

    @Bean
    HttpServer stripeStandInServer() throws IOException {
        HttpServer ret = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ret.createContext("/v1/", StripeStandIn::handle);
        ret.start();
        return ret;
    }

    // HttpServer has no no-argument stop for Spring to infer as its destroy method
    @Bean
    DisposableBean stripeStandInServerStop(HttpServer stripeStandInServer) {
        return () -> stripeStandInServer.stop(0);
    }

    @Bean
    @Primary
    StripeClient standInStripeClient(HttpServer stripeStandInServer) {
        return StripeClient.builder()
                           .setApiKey("sk_test_placeholder")
                           .setApiBase("http://localhost:" + stripeStandInServer.getAddress().getPort())
                           .build();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = form(body.isEmpty() ? exchange.getRequestURI().getRawQuery() : body);
        REQUESTS.add(new StripeRequest(method, path, params));
        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        JsonObject response;
        if (method.equals("POST") && idempotencyKey != null) {
            response = IDEMPOTENT_RESPONSES.computeIfAbsent(idempotencyKey, key -> respond(method, path, params));
        } else {
            response = respond(method, path, params);
        }
        respond(exchange, response);
    }

    private static JsonObject respond(String method, String path, Map<String, String> params) {
        JsonObject response;
        if (path.startsWith(SUBSCRIPTIONS_PATH)) {
            response = SUBSCRIPTIONS.get(path.substring(SUBSCRIPTIONS_PATH.length()));
        } else if (path.equals("/v1/billing/meters") && method.equals("GET")) {
            response = new JsonObject().put("object", "list").put("url", "/v1/billing/meters").put("has_more", false)
                                       .put("data", new JsonArray(new ArrayList<>(METERS)));
        } else if (path.equals("/v1/billing/meters")) {
            response = new JsonObject().put("id", "mtr_" + id()).put("object", "billing.meter")
                                       .put("event_name", params.get("event_name")).put("status", "active");
            METERS.add(response);
        } else if (path.startsWith("/v1/products")) {
            response = new JsonObject().put("id", path.equals("/v1/products") ? "prod_" + id() : path.substring("/v1/products/".length()))
                                       .put("object", "product");
        } else if (path.equals("/v1/prices")) {
            response = new JsonObject().put("id", "price_" + id()).put("object", "price");
        } else if (path.equals("/v1/customers")) {
            response = new JsonObject().put("id", "cus_" + id()).put("object", "customer");
        } else if (path.equals(CHECKOUT_SESSIONS_PATH)) {
            String sessionId = "cs_test_" + id();
            response = new JsonObject().put("id", sessionId).put("object", "checkout.session").put("status", "open")
                                       .put("client_reference_id", params.get("client_reference_id"))
                                       .put("url", "https://checkout.stripe.test/" + sessionId);
            CHECKOUT_SESSIONS.put(sessionId, response);
        } else if (path.startsWith(CHECKOUT_SESSIONS_PATH + "/") && path.endsWith(EXPIRE)) {
            response = CHECKOUT_SESSIONS.get(path.substring(CHECKOUT_SESSIONS_PATH.length() + 1, path.length() - EXPIRE.length()));
            if (response != null) {
                response.put("status", "expired");
            }
        } else if (path.startsWith(CHECKOUT_SESSIONS_PATH + "/")) {
            response = CHECKOUT_SESSIONS.get(path.substring(CHECKOUT_SESSIONS_PATH.length() + 1));
        } else if (path.equals("/v1/billing/meter_events")) {
            response = new JsonObject().put("object", "billing.meter_event")
                                       .put("event_name", params.get("event_name"))
                                       .put("identifier", params.get("identifier"));
        } else if (path.equals("/v1/invoices")) {
            response = new JsonObject().put("object", "list").put("url", "/v1/invoices").put("has_more", false)
                                       .put("data", new JsonArray().add(new JsonObject().put("id", "in_" + id())
                                                                                        .put("object", "invoice")
                                                                                        .put("customer", params.get("customer"))
                                                                                        .put("number", "KIN-0001")
                                                                                        .put("status", "paid")
                                                                                        .put("currency", "usd")
                                                                                        .put("total", 4900)
                                                                                        .put("amount_paid", 4900)
                                                                                        .put("amount_due", 0)
                                                                                        .put("created", 1_790_000_000L)));
        } else if (path.equals("/v1/billing_portal/sessions")) {
            String sessionId = "bps_" + id();
            response = new JsonObject().put("id", sessionId).put("object", "billing_portal.session")
                                       .put("url", "https://billing.stripe.test/" + sessionId);
        } else {
            response = null;
        }
        return response;
    }

    private static void respond(HttpExchange exchange, JsonObject response) throws IOException {
        int status = response != null ? 200 : 404;
        JsonObject body = response != null
                ? response
                : new JsonObject().put("error", new JsonObject().put("type", "invalid_request_error").put("message", "No such object"));
        byte[] bytes = body.encode().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> ret = new LinkedHashMap<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String pair : encoded.split("&")) {
                String[] parts = pair.split("=", 2);
                ret.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
        }
        return ret;
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
