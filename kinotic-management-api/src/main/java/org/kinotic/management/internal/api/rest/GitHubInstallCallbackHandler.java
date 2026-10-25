package org.kinotic.management.internal.api.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.management.internal.api.services.github.GitHubInstallStateService;
import org.kinotic.management.internal.api.services.github.StagedInstall;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/github/install/callback}: where GitHub sends the browser after an install of the App, with
 * {@code installation_id}, {@code state} and {@code code}. Forwards those parameters to the page that completes the
 * install with {@code completeInstall}: the absolute {@code returnTo} given to {@code startInstall} for that state,
 * so a UI built on Kinotic finishes its own install, and the portal's {@code /github/install/callback} otherwise.
 */
@Component
@RequiredArgsConstructor
public class GitHubInstallCallbackHandler implements SuppliesGatewayRoutes {

    private static final String PORTAL_CALLBACK_PATH = "/github/install/callback";

    private final GitHubInstallStateService stateService;
    private final KinoticDomainProperties domainProperties;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/github/install/callback").handler(this::forward);
    }

    private void forward(RoutingContext ctx) {
        String query = StringUtils.defaultString(ctx.request().query());
        StagedInstall staged = stateService.peek(ctx.request().getParam("state"));
        String returnTo = staged != null ? staged.getReturnTo() : null;
        String target;
        // startInstall admits an absolute returnTo only on an origin the CORS allowlist allows; a path is the portal's
        if (returnTo != null && !returnTo.startsWith("/")) {
            target = withQuery(returnTo, query);
        } else {
            target = withQuery(domainProperties.getDomain().getPortalBaseUrl() + PORTAL_CALLBACK_PATH, query);
        }
        ctx.response().setStatusCode(302).putHeader("Location", target).end();
    }

    // the query goes before any fragment, joined to a query the URL already carries
    private static String withQuery(String url, String query) {
        String ret = url;
        if (!query.isEmpty()) {
            int hash = url.indexOf('#');
            String base = hash >= 0 ? url.substring(0, hash) : url;
            String fragment = hash >= 0 ? url.substring(hash) : "";
            ret = base + (base.contains("?") ? "&" : "?") + query + fragment;
        }
        return ret;
    }
}
