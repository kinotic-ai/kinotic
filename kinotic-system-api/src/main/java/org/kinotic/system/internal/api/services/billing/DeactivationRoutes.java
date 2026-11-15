package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.services.OrganizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/billing/organizations/{organizationId}/deactivated}: whether an organization is deactivated, as
 * {@code {"deactivated": true}} or {@code {"deactivated": false}}, for the edge that serves the organization's sites.
 * Answers anyone, with no authentication; an organization that does not exist reads as not deactivated.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DeactivationRoutes implements SuppliesGatewayRoutes {

    /** The route, with the organization's id as its {@code organizationId} parameter. */
    public static final String PATH = "/api/billing/organizations/:organizationId/deactivated";

    // the edge keeps an answer this long, so a reactivated organization's sites are back within it
    private static final int MAX_AGE_SECONDS = 60;

    private final OrganizationService organizationService;

    @Override
    public void mountRoutes(Router router) {
        // ahead of the gateway's own /api/* handlers, which would ask the edge to authenticate
        router.get(PATH)
              .order(-1)
              .handler(this::answer);
    }

    private void answer(RoutingContext ctx) {
        String organizationId = ctx.pathParam("organizationId");
        Future.succeededFuture()
              .compose(v -> organizationService.findById(organizationId))
              .onSuccess(organization -> ctx.response()
                                            .putHeader("Content-Type", "application/json")
                                            .putHeader("Cache-Control", "public, max-age=" + MAX_AGE_SECONDS)
                                            .end(new JsonObject().put("deactivated", organization != null
                                                                                      && organization.getDeactivated() != null)
                                                                 .encode()))
              .onFailure(error -> {
                  log.error("Could not read whether organization {} is deactivated", organizationId, error);
                  ctx.response().setStatusCode(500).end();
              });
    }
}
