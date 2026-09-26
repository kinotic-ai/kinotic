package org.kinotic.core.api.security;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import org.kinotic.core.api.exceptions.AuthenticationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Binds each login in a browser session to the page that made it. One session cookie holds a login per
 * page origin, so a request is authenticated only by the login its own page made, and a session presented
 * on a host other than the one its logins were established on is destroyed.
 *
 * <p>The calling page is the request's {@code Origin}, or for a same-origin request, which carries no
 * {@code Origin}, the origin of its {@code Referer}. A request that names no page, as a client outside a
 * browser sends, has its own login slot.
 *
 * <p>{@link #handler(String)} resolves the binding for each request and must be mounted directly behind
 * the {@code SessionHandler} of every route that reads or writes a login; the other methods read what it
 * resolved.
 */
public final class SessionBinding {

    private static final String LOGIN_KEY_PREFIX = ConnectedInfo.class.getName() + "@";
    private static final String ORIGIN_KEY = SessionBinding.class.getName() + ".origin";
    private static final String PRESENTED_KEY = SessionBinding.class.getName() + ".presented";
    private static final String LOGIN_KEY = SessionBinding.class.getName() + ".login";

    private SessionBinding() {}

    /**
     * The handler that resolves each request's binding, for routes whose {@code SessionHandler} issues
     * {@code sessionCookieName}. It fails the request with {@code 401} and destroys the session when the
     * session holds a login established on another host.
     */
    public static Handler<RoutingContext> handler(String sessionCookieName) {
        return ctx -> {
            String origin = pageOrigin(ctx.request());
            if (origin != null) {
                ctx.put(ORIGIN_KEY, origin);
            }
            boolean hostMatches = true;
            // reading the session stores it, so a request without the cookie reads nothing
            if (ctx.request().getCookie(sessionCookieName) != null) {
                ctx.put(PRESENTED_KEY, Boolean.TRUE);
                Session session = ctx.session();
                String host = requestHost(ctx);
                hostMatches = logins(session).allMatch(login -> host != null && host.equalsIgnoreCase(login.getHost()));
                if (!hostMatches) {
                    // a cookie replayed against a host that did not issue it
                    session.destroy();
                } else if (session.get(loginKey(origin)) instanceof ConnectedInfo login) {
                    ctx.put(LOGIN_KEY, login);
                }
            }
            if (hostMatches) {
                ctx.next();
            } else {
                ctx.fail(401, new AuthenticationException("The session was not issued by this host"));
            }
        };
    }

    /** The origin of the page that sent the request, or {@code null} when it names none. */
    public static String origin(RoutingContext ctx) {
        return ctx.get(ORIGIN_KEY);
    }

    /** Whether the request presented the session cookie. */
    public static boolean sessionPresented(RoutingContext ctx) {
        return Boolean.TRUE.equals(ctx.get(PRESENTED_KEY));
    }

    /** The login bound to the page that sent the request, or {@code null} when that page has none. */
    public static ConnectedInfo connectedInfo(RoutingContext ctx) {
        return ctx.get(LOGIN_KEY);
    }

    /**
     * Binds {@code connectedInfo} to the page at {@code origin} in the request's session, stamped with the
     * host the request was addressed to. {@code origin} is the calling page's for a login the page makes
     * directly, and the page that started the flow for a login completed by a redirect.
     */
    public static void bind(RoutingContext ctx, String origin, ConnectedInfo connectedInfo) {
        connectedInfo.setHost(requestHost(ctx));
        ctx.session().put(loginKey(origin), connectedInfo);
        if (Objects.equals(origin, origin(ctx))) {
            ctx.put(LOGIN_KEY, connectedInfo);
        }
    }

    /**
     * Ends the login of the page that sent the request. The session, and its cookie, end once it holds no
     * page's login.
     */
    public static void unbind(RoutingContext ctx) {
        if (sessionPresented(ctx)) {
            Session session = ctx.session();
            session.remove(loginKey(origin(ctx)));
            ctx.remove(LOGIN_KEY);
            if (logins(session).findAny().isEmpty()) {
                session.destroy();
            }
        }
    }

    private static String pageOrigin(HttpServerRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null && "same-origin".equals(request.getHeader("Sec-Fetch-Site"))) {
            // a same-origin GET carries no Origin, as when the vite proxy serves a UI and its API from one origin
            origin = originOf(request.getHeader(HttpHeaders.REFERER));
        }
        return origin;
    }

    private static String originOf(String url) {
        String ret = null;
        if (url != null) {
            try {
                URI uri = new URI(url);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    ret = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
                }
            } catch (URISyntaxException e) {
                // an unparseable Referer names no page
            }
        }
        return ret;
    }

    private static String requestHost(RoutingContext ctx) {
        HostAndPort authority = ctx.request().authority();
        return authority != null ? authority.host() : null;
    }

    private static String loginKey(String origin) {
        return LOGIN_KEY_PREFIX + (origin != null ? origin : "");
    }

    private static Stream<ConnectedInfo> logins(Session session) {
        return session.data().entrySet().stream()
                      .filter(entry -> entry.getKey().startsWith(LOGIN_KEY_PREFIX))
                      .map(entry -> entry.getValue() instanceof ConnectedInfo login ? login : null)
                      .filter(Objects::nonNull);
    }
}
