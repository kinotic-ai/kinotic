package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import io.vertx.ext.web.Session;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.gateway.internal.endpoints.Services;

import java.util.function.Consumer;

/**
 * The Vert.x session a STOMP connection was established with, kept the way the connection's
 * {@link SessionKeepAliveMode} asks:
 * <ul>
 *   <li>{@code ACTIVITY}: the session lasts until it has gone unused, by requests on its cookie and by the frames of
 *       every connection on it, for its timeout. A browser login's session is then deleted and the connection ended.</li>
 *   <li>{@code CONNECTION}: the session lasts for as long as the connection is open.</li>
 *   <li>{@code NONE}: a session created for the connection ends with it, and the connection keeps no session
 *       alive.</li>
 * </ul>
 * A connection established on a browser login also ends once its session, or the login the session holds, has
 * ended. Every method must be called on the connection's event loop.
 */
@Slf4j
public class ConnectionSession {

    // how long before a session's expiry the review saves the time of the connection's last activity, so the put
    // reaches the store before the TTL of the previous put lapses
    private static final long SESSION_SAVE_MARGIN_MILLIS = 60_000;

    private final Services services;
    private final boolean createdHere;
    // the id of the browser login the connection was established on, or null when it authenticated with credentials
    private final String loginId;
    private final Consumer<Throwable> close;
    private Session session;
    private SessionKeepAliveMode keepAliveMode = SessionKeepAliveMode.ACTIVITY;
    // CONNECTION mode: when the session was last written to the store
    private long lastTouch = 0;
    // ACTIVITY mode: when the client last sent a frame
    private long lastActivityAt;
    // ACTIVITY mode: activity from this time on starts a review at once, since no review before the session's expiry
    // remains to save it; Long.MAX_VALUE while a review is in flight
    private long reviewActivityFrom;
    // the CONNECTION mode's periodic touch, or the next review
    private long timer = -1;
    // true once the gateway has ended the connection, as opposed to the client closing it
    private boolean ended;

    /**
     * @param services    the gateway's services, of which the session store and the session timeout are used
     * @param session     the session the upgrade request carried, or null when it carried none
     * @param createdHere true when the upgrade presented no session cookie and the session was created for it
     * @param loginId     the browser login the connection was established on, or null when it authenticated with
     *                    credentials
     * @param close       ends the connection with the cause given
     */
    public ConnectionSession(Services services, Session session, boolean createdHere, String loginId, Consumer<Throwable> close) {
        this.services = services;
        this.session = session;
        this.createdHere = createdHere;
        this.loginId = loginId;
        this.close = close;
    }

    /**
     * Keeps the session in the given mode from the connection's CONNECT frame on. The CONNECT frame counts as the
     * connection's first activity.
     * @throws AuthenticationException when the mode is not {@code NONE} and the upgrade carried no session
     */
    public void start(SessionKeepAliveMode mode) {
        if (mode != SessionKeepAliveMode.NONE && session == null) {
            throw new AuthenticationException("A Vert.x session is required unless session keep alive mode is NONE");
        }
        keepAliveMode = mode;
        if (mode == SessionKeepAliveMode.ACTIVITY) {
            activity();
        } else if (mode == SessionKeepAliveMode.CONNECTION) {
            // the upgrade response never reaches the store, so the connection's first write is this one
            touchSession();
            startTouchTimer();
        } else if (session != null && !createdHere) {
            // records no activity, so the reviews only watch for the session to expire or end
            reviewSession();
        }
    }

    /**
     * Records a frame the client sent, which keeps an {@code ACTIVITY} session in use.
     */
    public void activity() {
        if (keepAliveMode == SessionKeepAliveMode.ACTIVITY) {
            if (session == null) {
                log.error("Session is null while sessionKeepAliveMode is ACTIVITY");
                throw new IllegalStateException("Internal server error");
            }
            // Only the time is kept; the review saves it to the store shortly before the session would expire, so a
            // busy connection writes the store about once per timeout
            lastActivityAt = System.currentTimeMillis();
            if (lastActivityAt >= reviewActivityFrom) {
                reviewSession();
            }
        }
    }

    /**
     * Ends the connection with the cause given, on the gateway's decision: its login was signed out, its
     * authorization was revoked, or its session ended.
     */
    public void end(Throwable cause) {
        ended = true;
        close.accept(cause);
    }

    /**
     * Stops keeping the session, once the connection has ended. When the client closed an {@code ACTIVITY}
     * connection, its activity not yet saved is saved to the session, while it lives, for the other connections and
     * requests on it. A {@code NONE} session created for the connection is removed from the store.
     */
    public void stop() {
        if (timer != -1) {
            services.vertx.cancelTimer(timer);
            timer = -1;
        }
        // A connection the gateway ended writes nothing: a sign-out is writing the session at the same moment, and a
        // put racing it would restore the login the sign-out removed
        if (!ended && keepAliveMode == SessionKeepAliveMode.ACTIVITY && session != null && !createdHere) {
            saveLastActivity(session.id(), lastActivityAt);
        }
        // a login session the client presented belongs to the browser, not to this connection
        if (keepAliveMode == SessionKeepAliveMode.NONE && session != null && createdHere) {
            // The Vert.x SessionHandler deletes a destroyed session from the store when the response
            // ends. A WebSocket's response ended at the upgrade, so the store entry is removed here.
            String sessionId = session.id();
            services.sessionStore.delete(sessionId)
                                 .onFailure(throwable -> log.warn("Session {} could not be removed from the store", sessionId, throwable));
            session.destroy();
        }
        session = null;
    }

    // Reads the stored session and ends the connection once the session has ended. A session that has gone unused,
    // by requests on its cookie and by every connection's frames, for its timeout has expired: a browser login's is
    // deleted and the connection closed. While the session is in use, the time of the connection's last activity is
    // written to it a margin before it would expire, and the next review is set a margin before the new expiry; inside the
    // margin, the last review runs at the expiry itself.
    private void reviewSession() {
        reviewActivityFrom = Long.MAX_VALUE;
        if (timer != -1) {
            services.vertx.cancelTimer(timer);
            timer = -1;
        }
        String sessionId = session.id();
        long saveMargin = Math.min(SESSION_SAVE_MARGIN_MILLIS, session.timeout() / 4);
        services.sessionStore.get(sessionId).compose(stored -> {
            // the session's expiry, or null once the review has nothing more to watch
            Future<Long> ret = Future.succeededFuture();
            // the connection ended while the store answered
            if (session != null) {
                AuthenticationException endedCause = sessionEnded(stored);
                if (endedCause != null) {
                    end(endedCause);
                } else if (stored != null) {
                    long lastUse = SessionBinding.lastUse(stored);
                    long expiresAt = Math.max(lastActivityAt, lastUse) + stored.timeout();
                    if (System.currentTimeMillis() >= expiresAt) {
                        // a session created for this connection lapses on its TTL, and the connection goes on without it
                        if (!createdHere) {
                            ret = services.sessionStore.delete(sessionId).map(v -> {
                                end(new AuthenticationException("The session the connection was established with has expired"));
                                return null;
                            });
                        }
                    } else if (lastActivityAt > lastUse) {
                        SessionBinding.recordActivity(stored, lastActivityAt);
                        ret = services.sessionStore.put(stored).map(expiresAt);
                    } else {
                        ret = Future.succeededFuture(expiresAt);
                    }
                }
            }
            return ret;
        }).onComplete(ar -> {
            // the connection ended while the store answered
            if (session == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (ar.failed()) {
                // retried well inside the margin, so a save that failed still lands before the previous put's TTL lapses
                log.warn("Session {} could not be reviewed in the store", sessionId, ar.cause());
                scheduleReview(now + saveMargin / 4);
            } else if (ar.result() != null) {
                long saveAt = ar.result() - saveMargin;
                if (now < saveAt) {
                    reviewActivityFrom = saveAt;
                    scheduleReview(saveAt);
                } else {
                    reviewActivityFrom = now;
                    scheduleReview(ar.result());
                }
            }
        });
    }

    private void scheduleReview(long reviewAt) {
        timer = services.vertx.setTimer(Math.max(reviewAt - System.currentTimeMillis(), 1), timerId -> {
            timer = -1;
            reviewSession();
        });
    }

    // The activity would otherwise end with the connection, and another connection or request on the session could
    // then see it expire sooner than its timeout after that activity. A session that has ended is never written back.
    private void saveLastActivity(String sessionId, long activityAt) {
        services.sessionStore.get(sessionId).compose(stored -> {
            Future<Void> ret = Future.succeededFuture();
            if (stored != null && sessionEnded(stored) == null && activityAt > SessionBinding.lastUse(stored)) {
                SessionBinding.recordActivity(stored, activityAt);
                ret = services.sessionStore.put(stored);
            }
            return ret;
        }).onFailure(throwable -> log.warn("The last activity on session {} could not be saved", sessionId, throwable));
    }

    // a quarter of the timeout, so a touch that fails is retried before the stored copy expires
    private long touchInterval() {
        return services.apiGatewayProperties.getSessionTimeout() / 4;
    }

    // SessionHandler flushes a session to the store when a response ends, which on a WebSocket happened
    // once, at the upgrade. Every touch after that has to reach the store itself or the clustered entry
    // expires under the open connection. The write is rate limited to a fraction of the timeout.
    private void touchSession() {
        session.setAccessed();
        long now = System.currentTimeMillis();
        if (now - lastTouch >= touchInterval()) {
            lastTouch = now;
            flushSession(session);
        }
    }

    // The stored copy is refreshed rather than the local one: it carries the version the store expects and
    // whatever a request on the same cookie has put since. A session the store no longer has, deleted by a
    // logout or expired, is never written back, because a put with no stored entry would re-create it; a
    // connection a browser login established ends with it, since the login is gone with the session. It ends too
    // once the stored session no longer holds its login: a logout removes the login before it publishes the
    // revocation, so this read ends a connection the revocation missed, lost on its way or delivered before the
    // connection registered for it.
    private void flushSession(Session flushed) {
        services.sessionStore.get(flushed.id()).compose(stored -> {
            Future<Void> ret = Future.succeededFuture();
            if (session == flushed) {
                AuthenticationException endedCause = sessionEnded(stored);
                if (endedCause != null) {
                    end(endedCause);
                } else if (stored == null) {
                    log.debug("Session {} is no longer in the store and is not refreshed", flushed.id());
                } else {
                    session = stored;
                    stored.setAccessed();
                    ret = services.sessionStore.put(stored);
                }
            }
            return ret;
        }).onFailure(throwable -> log.warn("Session {} could not be refreshed in the store", flushed.id(), throwable));
    }

    // The reason the connection ends, given the stored copy of its session, or null while the connection may go on
    private AuthenticationException sessionEnded(Session stored) {
        AuthenticationException ret = null;
        if (stored == null) {
            if (!createdHere) {
                ret = new AuthenticationException("The session the connection was established with has ended");
            }
        } else if (loginId != null && !SessionBinding.holdsLogin(stored, loginId)) {
            ret = new AuthenticationException("The login the connection was established on has ended");
        }
        return ret;
    }

    private void startTouchTimer() {
        if (timer != -1) {
            log.error("Session-touch timer already started");
            throw new IllegalStateException("Internal server error");
        }
        timer = services.vertx.setPeriodic(touchInterval(), event -> {
            if (session == null) {
                log.error("Session is null while session-touch timer is active");
                throw new IllegalStateException("Internal server error");
            }
            touchSession();
        });
    }
}
