package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import lombok.extern.slf4j.Slf4j;
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
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The app server's {@link AuthEndpointSupport}. Every request the app server serves is addressed to one
 * application's API host, {@code <organizationId>--<applicationId>} under {@code kinotic.domain.appApiBaseUrl},
 * and {@link #applicationKey} names that application; the browser flows send the user to its primary UI.
 */
@Slf4j
@Component
public class ApplicationAuthEndpointSupport extends AuthEndpointSupport {

    /**
     * Path on the shared host, {@code kinotic.domain.appApiBaseUrl}, that begins a sign-in through an application's
     * configuration that uses one of the system's social registrations; {@link #socialAuthorizeUrl} builds the URL a start answers with.
     */
    public static final String SOCIAL_AUTHORIZE_PATH = "/api/auth/app/login/social/authorize";

    /**
     * Session key of the value that binds a social sign-in in transit to the browser that started it: the
     * application's API host mints it into the starting browser's session and the shared host holds it for the
     * flow it runs, so the completion at the application's host is answered only to the browser that presents it.
     */
    public static final String SOCIAL_LOGIN_BINDING_SESSION_KEY = "socialLoginBinding";

    private final AppServerProperties properties;
    private final KinoticDomainProperties domainProperties;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationSignUpService signUpService;

    public ApplicationAuthEndpointSupport(AppServerProperties properties,
                                          KinoticDomainProperties domainProperties,
                                          ApplicationRepository applicationRepository,
                                          TenantRepository tenantRepository,
                                          ApplicationSignUpService signUpService,
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
        this.signUpService = signUpService;
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
     * local origin {@code kinotic.appServer.localUiOriginPattern} admits. A site is named by its origin alone, an
     * {@code http} or {@code https} scheme, its host and an optional port, so a value that also carries a path,
     * query, fragment or user info names none.
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
            URI uri = URI.create(origin);
            // the links and redirects built from a page's origin append paths to it, so anything after the host and
            // port would carry into them
            boolean bare = ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getRawUserInfo() == null
                    && StringUtils.isEmpty(uri.getRawPath())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
            host = bare ? uri.getHost() : null;
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
        String sitesDomain = domainProperties.getDomain().getPublishedSites().getDomain();
        boolean ret = false;
        if (StringUtils.isNotBlank(sitesDomain)) {
            String suffix = "." + sitesDomain.toLowerCase(Locale.ROOT);
            String lower = host.toLowerCase(Locale.ROOT);
            ret = lower.endsWith(suffix) && lower.substring(0, lower.length() - suffix.length()).split("\\.", -1).length == 2;
        }
        return ret;
    }

    /**
     * The origin of the page that sent the request, which a browser flow is started and completed from. A request
     * no page sent is answered with {@code 400}.
     *
     * @return the origin, or {@code null} once the request has been answered
     */
    public String requirePageOrigin(RoutingContext ctx) {
        String ret = SessionBinding.origin(ctx);
        if (ret == null) {
            respondError(ctx, 400, "The request is sent by a page");
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
     *         or is a site of another tenant than the one named, and with {@code 404} when the application does
     *         not exist or has no such tenant
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

    /**
     * The scope of a sign-in to the application's tenant {@code tenantId}, or to the application itself when it is
     * {@code null}, for a flow returning to the scope its start checked the page of.
     *
     * @return the scope; fails the request with {@code 404} when the application does not exist or has no such tenant
     */
    public Future<LoginScope> loginScope(ApplicationKey applicationKey, String tenantId) {
        Future<Application> application = applicationRepository.findById(applicationKey.applicationId(), applicationKey.organizationId());
        Future<Tenant> tenant = tenantId != null
                ? tenantRepository.findById(applicationKey.applicationId(), tenantId, applicationKey.organizationId())
                : Future.succeededFuture();
        return Future.all(application, tenant).map(v -> {
            if (application.result() == null) {
                throw new HttpException(404, "There is no application " + HostLabelUtil.label(applicationKey));
            }
            if (tenantId != null && tenant.result() == null) {
                throw new HttpException(404, "The application has no tenant " + tenantId);
            }
            return new LoginScope(application.result(), tenantId);
        });
    }

    /**
     * The user of {@code scope} the identity {@code configuration}'s provider verified signs in as, created on its first
     * sign-in where the configuration signs people up and the scope admits them (see
     * {@link ApplicationSignUpService#findOrCreateOidcUser}), or {@code null} when there is none. A user that would be
     * created under an email the application holds under another method fails with an {@link OidcCallbackException}
     * carrying {@link OidcErrorCodes#USER_EXISTS}, and one under an email the claims do not prove is the person's with
     * {@link OidcErrorCodes#EMAIL_NOT_VERIFIED}, and a disabled user the identity would claim with {@link OidcErrorCodes#USER_DISABLED}.
     *
     * @param claims the claims the provider verified, which {@link #verifiedSubject} has accepted
     */
    public Future<ApplicationParticipantIdentity> findOrCreateUser(LoginScope scope, OwnedOidcConfiguration configuration, Map<String, Object> claims) {
        return signUpService.findOrCreateOidcUser(scope, configuration, claims)
                            .recover(err -> Future.failedFuture(switch (err) {
                                case ExistingUserException _ -> new OidcCallbackException(OidcErrorCodes.USER_EXISTS);
                                case UnverifiedEmailException _ -> new OidcCallbackException(OidcErrorCodes.EMAIL_NOT_VERIFIED, configuration.getProvider());
                                case UserDisabledException _ -> new OidcCallbackException(OidcErrorCodes.USER_DISABLED);
                                default -> err;
                            }));
    }

    /**
     * Answers a request whose handling failed: a failure naming a status, such as a scope refused with {@code 403}
     * or {@code 404}, with that status through the router's failure handler, and any other with {@code 500} and
     * {@code message}.
     */
    public void fail(RoutingContext ctx, Throwable err, String message) {
        if (err instanceof HttpException || err instanceof AuthorizationException) {
            ctx.fail(err);
        } else {
            log.warn("{} at {}: {}", message, ctx.request().authority(), err.getMessage());
            respondError(ctx, 500, message);
        }
    }

    /**
     * The URL on the shared host that begins a sign-in to {@code scope} through the application's configuration
     * {@code configId}, which signs in through Kinotic's registration with its provider, for the page at {@code origin}, in the browser whose session at this host
     * holds {@code binding} under {@link #SOCIAL_LOGIN_BINDING_SESSION_KEY}.
     */
    public String socialAuthorizeUrl(LoginScope scope, String configId, String origin, String binding) {
        ApplicationKey applicationKey = scope.applicationKey();
        String ret = domainProperties.getDomain().getAppApiBaseUrl() + SOCIAL_AUTHORIZE_PATH
                + "?organizationId=" + URLEncoder.encode(applicationKey.organizationId(), StandardCharsets.UTF_8)
                + "&applicationId=" + URLEncoder.encode(applicationKey.applicationId(), StandardCharsets.UTF_8)
                + "&configurationId=" + URLEncoder.encode(configId, StandardCharsets.UTF_8)
                + "&origin=" + URLEncoder.encode(origin, StandardCharsets.UTF_8)
                + "&binding=" + URLEncoder.encode(binding, StandardCharsets.UTF_8);
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
        return authority != null ? HostLabelUtil.fromHost(authority.host(), domainProperties.getDomain().getAppApiBaseUrl()) : null;
    }
}
