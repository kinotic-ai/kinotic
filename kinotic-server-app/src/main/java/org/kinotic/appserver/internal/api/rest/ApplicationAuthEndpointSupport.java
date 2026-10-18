package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.SiteAddress;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The app server's {@link AuthEndpointSupport}. Every request the app server serves is addressed to one
 * application's API host, {@code <organizationId>--<applicationId>} under {@code kinotic.appServer.apiBaseUrl},
 * and {@link #applicationKey} names that application; the browser flows send the user to its primary UI.
 */
@Component
public class ApplicationAuthEndpointSupport extends AuthEndpointSupport {

    /**
     * Path on the shared host, {@code kinotic.appServer.apiBaseUrl}, that begins a sign-in through one of the
     * platform's social registrations; {@link #socialAuthorizeUrl} builds the URL a start answers with.
     */
    public static final String SOCIAL_AUTHORIZE_PATH = "/api/auth/app/login/social/authorize";

    private final AppServerProperties properties;
    private final KinoticDomainProperties domainProperties;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;

    public ApplicationAuthEndpointSupport(AppServerProperties properties,
                                          KinoticDomainProperties domainProperties,
                                          ApplicationRepository applicationRepository,
                                          TenantRepository tenantRepository,
                                          KinoticJwtIssuer jwtIssuer,
                                          OidcConfigurationService oidcConfigurationService,
                                          OidcFlowOrchestrator oidcFlowOrchestrator,
                                          ParticipantIdentityService identityService,
                                          RefreshTokenService refreshTokenService) {
        super(jwtIssuer, oidcConfigurationService, oidcFlowOrchestrator, identityService, refreshTokenService);
        this.properties = properties;
        this.domainProperties = domainProperties;
        this.applicationRepository = applicationRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * The application whose API host the request was addressed to.
     *
     * @param ctx the request
     * @return the application
     * @throws HttpException with {@code 404} when the request names no application's API host
     */
    public ApplicationKey applicationKey(RoutingContext ctx) {
        ApplicationKey ret = findApplicationKey(ctx);
        if (ret == null) {
            throw new HttpException(404);
        }
        return ret;
    }

    /**
     * Whether the page at {@code origin} is one of the application's UIs: one of its published sites, or a
     * local origin {@code kinotic.appServer.localUiOriginPattern} admits.
     */
    public boolean isApplicationUi(ApplicationKey applicationKey, String origin) {
        Pattern localUiOrigins = properties.getLocalUiOriginPattern();
        return site(applicationKey, origin) != null
                || (localUiOrigins != null && localUiOrigins.matcher(origin).matches());
    }

    // the application's published site the page at origin is, or null for any other page
    private SiteAddress site(ApplicationKey applicationKey, String origin) {
        String host;
        try {
            host = URI.create(origin).getHost();
        } catch (IllegalArgumentException e) {
            // the origin may come from a query parameter, and a malformed one names no page
            host = null;
        }
        // the site labels are read only from a host directly under the sites domain: a foreign host that
        // imitates them is not one of the application's pages
        SiteAddress site = host != null && isSiteHost(host) ? HostLabelUtil.fromSiteHost(host) : null;
        return site != null && site.application().equals(applicationKey) ? site : null;
    }

    // <site>.<organizationId>.<sitesDomain>, and nothing else
    private boolean isSiteHost(String host) {
        String sitesDomain = domainProperties.getDomain().getSiteDomain().getDomain();
        boolean ret = false;
        if (StringUtils.isNotBlank(sitesDomain)) {
            String suffix = "." + sitesDomain.toLowerCase(Locale.ROOT);
            String lower = host.toLowerCase(Locale.ROOT);
            ret = lower.endsWith(suffix) && lower.substring(0, lower.length() - suffix.length()).split("\\.", -1).length == 2;
        }
        return ret;
    }

    /**
     * The scope the request signs in to: the application the request was addressed to, from the page that
     * sent it, and the tenant of the application when that page is one of the tenant's sites or, from a page
     * that is no site, when the request names the tenant as {@code requestedTenantId}.
     *
     * @param requestedTenantId the tenant the request names, or {@code null}
     * @return the scope; fails the request with {@code 403} when the page is not one of the application's UIs
     *         or is a site of another tenant than the one named, and with {@code 404} when the application
     *         has no such tenant
     */
    public Future<LoginScope> loginScope(RoutingContext ctx, String requestedTenantId) {
        ApplicationKey applicationKey = applicationKey(ctx);
        String origin = SessionBinding.origin(ctx);
        // a request naming no page comes from a client outside a browser, which signs in to what it names
        return origin == null
                ? loginScope(applicationKey, requestedTenantId)
                : loginScope(applicationKey, origin, requestedTenantId);
    }

    /**
     * {@link #loginScope(RoutingContext, String)} for the page at {@code origin}, named by the caller when the
     * request carries no {@code Origin} of its own.
     */
    public Future<LoginScope> loginScope(ApplicationKey applicationKey, String origin, String requestedTenantId) {
        // a login authenticates the page that made it, so a login made from another application's UI would
        // hand that UI this application's user
        if (!isApplicationUi(applicationKey, origin)) {
            throw new AuthorizationException("The page is not a UI of this application");
        }
        SiteAddress site = site(applicationKey, origin);
        String siteTenant = site != null ? site.tenantId() : null;
        if (siteTenant != null && requestedTenantId != null && !siteTenant.equals(requestedTenantId)) {
            throw new AuthorizationException("The page is a site of another tenant");
        }
        return loginScope(applicationKey, siteTenant != null ? siteTenant : requestedTenantId);
    }

    // the scope, once the tenant it names is one of the application's
    private Future<LoginScope> loginScope(ApplicationKey applicationKey, String tenantId) {
        Future<Tenant> tenant = tenantId != null
                ? tenantRepository.findById(tenantId, applicationKey.applicationId(), applicationKey.organizationId())
                : Future.succeededFuture();
        return tenant.map(found -> {
            if (tenantId != null && found == null) {
                throw new HttpException(404, "The application has no tenant " + tenantId);
            }
            return new LoginScope(applicationKey, tenantId);
        });
    }

    /**
     * The URL on the shared host that begins a sign-in to {@code scope} through the platform social
     * registration {@code configId}, for the page at {@code origin}.
     */
    public String socialAuthorizeUrl(LoginScope scope, String configId, String origin) {
        ApplicationKey applicationKey = scope.application();
        String ret = properties.getApiBaseUrl() + SOCIAL_AUTHORIZE_PATH
                + "?organizationId=" + URLEncoder.encode(applicationKey.organizationId(), StandardCharsets.UTF_8)
                + "&applicationId=" + URLEncoder.encode(applicationKey.applicationId(), StandardCharsets.UTF_8)
                + "&configurationId=" + URLEncoder.encode(configId, StandardCharsets.UTF_8)
                + "&origin=" + URLEncoder.encode(origin, StandardCharsets.UTF_8);
        if (scope.tenantId() != null) {
            ret += "&tenantId=" + URLEncoder.encode(scope.tenantId(), StandardCharsets.UTF_8);
        }
        return ret;
    }

    /**
     * {@inheritDoc} The UI is the primary UI of the application the request is addressed to.
     *
     * @throws HttpException with {@code 404} when the request names no application's API host, or names an
     *                       application that does not exist
     * @throws IllegalArgumentException while the application has no primary UI designated, which the
     *                                  gateway answers with {@code 400}
     */
    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        ApplicationKey applicationKey = findApplicationKey(ctx);
        Future<Application> application = applicationKey != null
                ? applicationRepository.findById(applicationKey.applicationId(), applicationKey.organizationId())
                : Future.succeededFuture();
        return application.map(found -> {
            if (found == null) {
                throw new HttpException(404);
            }
            Validate.isTrue(found.getPrimaryUiUrl() != null, "The application '%s' has no primary UI", found.getId());
            return found.getPrimaryUiUrl() + path;
        });
    }

    // null when the request was addressed to a host that is no application's API host
    private ApplicationKey findApplicationKey(RoutingContext ctx) {
        // authority() is the request's Host header, parsed; null on an HTTP/1.0 request that sent none
        HostAndPort authority = ctx.request().authority();
        return authority != null ? HostLabelUtil.fromHost(authority.host(), properties.getApiBaseUrl()) : null;
    }
}
