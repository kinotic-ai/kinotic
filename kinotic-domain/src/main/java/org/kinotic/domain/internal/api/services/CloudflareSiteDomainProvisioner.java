package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.CloudflareSiteDomainProperties;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.services.SiteDomainProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Provisions each organization's sites subdomain as one proxied wildcard DNS record, {@code *.<org>} under the
 * sites domain, in the Cloudflare zone {@code kinotic.domain.siteDomain.cloudflare.zoneId}. The record points
 * at a placeholder address because the zone's sites Worker answers every request to it; its existence is what
 * the zone's Total TLS issues the organization's wildcard certificate from. A record is looked up by its name
 * before it is created, so ensuring an organization twice creates one record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.domain.siteDomain.cloudflare.enabled", havingValue = "true")
public class CloudflareSiteDomainProvisioner implements SiteDomainProvisioner {

    private static final String API_BASE_URL = "https://api.cloudflare.com/client/v4";
    /**
     * TEST-NET-1 (RFC 5737): never routed, which is what a host the Worker answers in full points at.
     */
    private static final String PLACEHOLDER_ADDRESS = "192.0.2.1";
    private static final int AUTOMATIC_TTL = 1;

    private final KinoticDomainProperties properties;
    private final Vertx vertx;
    private WebClient webClient;
    // the zone's name, read once: the record names the API lists are the name with the zone appended
    private volatile Future<String> zoneName;

    @PostConstruct
    public void start() {
        CloudflareSiteDomainProperties cloudflare = cloudflare();
        Validate.notBlank(cloudflare.getApiToken(), "kinotic.domain.siteDomain.cloudflare.apiToken is required");
        Validate.notBlank(cloudflare.getZoneId(), "kinotic.domain.siteDomain.cloudflare.zoneId is required");
        Validate.notBlank(sitesDomain(), "kinotic.domain.siteDomain.domain is required");
        webClient = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false));
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    @Override
    public Future<Void> ensure(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return recordName(organizationId)
                .compose(name -> findRecord(name)
                        .compose(existing -> existing != null ? Future.succeededFuture() : createRecord(name, organizationId)));
    }

    // the record's full name, *.<org> followed by the sites domain's labels within the zone
    private Future<String> recordName(String organizationId) {
        return zoneName().map(zone -> {
            String domain = sitesDomain().toLowerCase(Locale.ROOT);
            Validate.isTrue(domain.equals(zone) || domain.endsWith("." + zone),
                            "The sites domain %s is not in the Cloudflare zone %s", domain, zone);
            return "*." + organizationId + "." + domain;
        });
    }

    private Future<JsonObject> findRecord(String name) {
        return request(webClient.getAbs(zoneUrl("/dns_records")).addQueryParam("name", name).addQueryParam("type", "A"))
                .map(result -> {
                    JsonArray records = result.getJsonArray("result", new JsonArray());
                    return records.isEmpty() ? null : records.getJsonObject(0);
                });
    }

    private Future<Void> createRecord(String name, String organizationId) {
        JsonObject record = new JsonObject()
                .put("type", "A")
                .put("name", name)
                .put("content", PLACEHOLDER_ADDRESS)
                .put("proxied", true)
                .put("ttl", AUTOMATIC_TTL)
                .put("comment", "Sites of organization " + organizationId);
        return request(webClient.postAbs(zoneUrl("/dns_records")), record)
                .onSuccess(v -> log.info("Created the sites record {} for organization {}", name, organizationId))
                .mapEmpty();
    }

    private Future<String> zoneName() {
        Future<String> ret = zoneName;
        if (ret == null) {
            ret = request(webClient.getAbs(zoneUrl("")))
                    .map(result -> result.getJsonObject("result").getString("name").toLowerCase(Locale.ROOT));
            zoneName = ret;
            // a failed read is not kept, so the next call reads again
            ret.onFailure(err -> zoneName = null);
        }
        return ret;
    }

    private Future<JsonObject> request(HttpRequest<io.vertx.core.buffer.Buffer> request) {
        return authorized(request).send().compose(CloudflareSiteDomainProvisioner::result);
    }

    private Future<JsonObject> request(HttpRequest<io.vertx.core.buffer.Buffer> request, JsonObject body) {
        return authorized(request).sendJsonObject(body).compose(CloudflareSiteDomainProvisioner::result);
    }

    private HttpRequest<io.vertx.core.buffer.Buffer> authorized(HttpRequest<io.vertx.core.buffer.Buffer> request) {
        return request.bearerTokenAuthentication(cloudflare().getApiToken());
    }

    // Cloudflare answers every call with {success, errors, result}; a refusal names its reasons in errors
    private static Future<JsonObject> result(HttpResponse<io.vertx.core.buffer.Buffer> response) {
        JsonObject body;
        try {
            body = response.bodyAsJsonObject();
        } catch (RuntimeException e) {
            body = null;
        }
        Future<JsonObject> ret;
        if (body != null && Boolean.TRUE.equals(body.getBoolean("success"))) {
            ret = Future.succeededFuture(body);
        } else {
            String errors = body != null ? String.valueOf(body.getJsonArray("errors")) : response.bodyAsString();
            ret = Future.failedFuture(new IllegalStateException("Cloudflare answered " + response.statusCode() + ": " + errors));
        }
        return ret;
    }

    private String zoneUrl(String path) {
        return API_BASE_URL + "/zones/" + cloudflare().getZoneId() + path;
    }

    private String sitesDomain() {
        return properties.getDomain().getSiteDomain().getDomain();
    }

    private CloudflareSiteDomainProperties cloudflare() {
        return properties.getDomain().getSiteDomain().getCloudflare();
    }
}
