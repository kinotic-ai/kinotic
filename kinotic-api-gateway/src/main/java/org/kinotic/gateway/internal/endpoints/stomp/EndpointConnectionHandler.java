


package org.kinotic.gateway.internal.endpoints.stomp;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.api.security.Session;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.api.CliSecurityService;
import org.kinotic.gateway.internal.endpoints.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Created by Navid Mitchell on 11/3/20
 */
public class EndpointConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(EndpointConnectionHandler.class);
    private static final String SESSION_COOKIE_PATH = "/v1";
    private final SecurityService securityService;
    private final Services services;
    private final Map<String, EventConsumer> subscriptions = new HashMap<>();
    private Session session;
    private boolean sessionFromCookie = false;
    private boolean secureCookies = false;
    private SessionKeepAliveMode sessionKeepAliveMode = SessionKeepAliveMode.ACTIVITY;
    private long sessionTimer = -1;

    public EndpointConnectionHandler(Services services) {
        this.services = services;

        if(services.apiGatewayProperties.isEnableCLIConnections()){
            this.securityService = new CliSecurityService(services.securityService);
        }else{
            this.securityService = services.securityService;
        }
    }

    public CompletableFuture<MultiMap> handshake(RoutingContext routingContext) {
        String sessionId = findSessionIdCookie(transportHeaders);
        sessionKeepAliveMode = getHandshakeSessionKeepAliveMode(transportHeaders);
        secureCookies = isSecureRequest(transportHeaders);

        if (sessionId != null) {
            return services.sessionManager
                    .findSession(sessionId)
                    .handle((session, throwable) -> {
                        if(throwable != null){
                            throw new AuthenticationException("Could not authenticate with the given Session id", throwable);
                        }else{
                            sessionFromCookie = true;
                            this.session = session;
                            return createHandshakeResponseHeaders(session);
                        }
                    });
        }

        return securityService.authenticate(toCaseInsensitiveMap(transportHeaders))
                              .handle((participant, throwable) -> {
                                  if(throwable != null){
                                      if(!(throwable instanceof AuthenticationException)) {
                                          throw new AuthenticationException("Could not authenticate with the given credentials", throwable);
                                      }else{
                                          throw (AuthenticationException) throwable;
                                      }
                                  }else {
                                      return participant;
                                  }
                              })
                              .thenCompose(participant -> services.sessionManager.create(participant, UUID.randomUUID().toString()))
                              .thenApply(session -> {
                                  this.session = session;
                                  return createHandshakeResponseHeaders(session);
                              });
    }

    public CompletableFuture<Map<String, String>> connect(Map<String, String> connectHeaders) {
        if (session != null) {
            try {
                sessionKeepAliveMode = getSessionKeepAliveMode(connectHeaders);
                if(sessionKeepAliveMode == SessionKeepAliveMode.NONE && sessionFromCookie){
                    return CompletableFuture.failedFuture(new AuthenticationException("Session cookie provided but also requested session keep alive NONE, this is not allowed"));
                }

                session.touch();
                if (sessionKeepAliveMode == SessionKeepAliveMode.CONNECTION) {
                    startSessionTouchTimer();
                }
                return CompletableFuture.completedFuture(Map.of(EventConstants.CONNECTED_INFO_HEADER,
                                                                createConnectedInfoJson(session)));
            } catch (JacksonException e) {
                return CompletableFuture.failedFuture(e);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(new AuthenticationException("Invalid session keep alive mode", e));
            }
        }

        return CompletableFuture.failedFuture(new AuthenticationException("Client must authenticate before sending a CONNECT frame"));
    }

    public void removeSession() {
        // There will not be a session if authentication was not successful.
        if (session != null) {
            cancelSessionTimer();

            services.sessionManager
                    .removeSession(session.sessionId())
                    .handle((BiFunction<Boolean, Throwable, Void>) (aBoolean, throwable) -> {
                        if (throwable != null) {
                            log.error("Could not remove sessionId: {}", session.sessionId(), throwable);
                        }
                        return null;
                    });

            session = null;
        }
    }

    public Future<Void> send(Event<byte[]> incomingEvent) {
        session.touch();

        if (!session.sendAllowed(incomingEvent.cri())) {
            return Future.failedFuture(new AuthorizationException("Not Authorized to send to " + incomingEvent.cri()));
        }

        if (incomingEvent.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            try {

                // FIXME: when the invocation is local this happens for no reason. If the event stays on the local bus we shouldn't do this..
                incomingEvent.metadata().put(EventConstants.SENDER_HEADER, services.jsonMapper.writeValueAsString(session.participant()));

                // make sure reply-to if present is scoped to sender
                // FIXME: a reply should not need a reply, therefore a replyCri probably should not be a EventConstants.SERVICE_DESTINATION_PREFIX
                validateReplyToForServiceRequest(incomingEvent);

                return services.eventBusService
                        .sendWithAck(incomingEvent)
                        .recover(throwable -> {
                            // map errors that occurred because no Service invoker was listening
                            if (throwable instanceof ReplyException replyException) {
                                if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                                    throwable = new RpcMissingServiceException(throwable);
                                }
                            }
                            try {
                                Event<byte[]> convertedEvent = services.exceptionConverter.convert(incomingEvent.metadata(), throwable);
                                // since we don't know the subscription id used by the stomp client for this request we send through the eventbus
                                services.eventBusService.send(convertedEvent);
                                return Future.succeededFuture();
                            } catch (Exception ex) {
                                if(log.isDebugEnabled()){
                                    log.debug("Exception occurred converting exception\n{}",
                                              EventUtil.toString(incomingEvent, true),
                                              throwable);
                                }
                                return Future.failedFuture(ex);
                            }
                        });

            } catch (Exception e) {
                return Future.failedFuture(e);
            }

        } else if (incomingEvent.cri().scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)) {

            return services.eventStreamService.send(incomingEvent);

        } else {
            return Future.failedFuture(new IllegalArgumentException("CRI scheme not supported"));
        }
    }

    public void shutdown() {
        if(this.sessionKeepAliveMode == SessionKeepAliveMode.NONE){
            removeSession();
        }else{
            cancelSessionTimer();
            subscriptions.forEach((s, eventConsumer) -> eventConsumer.unregister());
            subscriptions.clear();
        }
    }

    public void subscribe(CRI cri,
                          String subscriptionIdentifier,
                          StompSubscriptionHandler subscriptionHandler) {
        Validate.notNull(cri, "CRI must not be null");
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");
        Validate.notNull(subscriptionHandler, "subscriptionHandler must not be null");

        session.touch();

        if (!session.subscribeAllowed(cri)) {
            throw new AuthorizationException("Not Authorized to subscribe to " + cri);
        }

        if (cri.scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            EventConsumer eventConsumer = services.eventBusService.listen(cri.baseResource());
            eventConsumer.handler(event -> {
                        // If reply-to is set we implicitly allow the subscriber to send a single message to the given destination
                        // Reply-To is known to be scoped to the sender because there is a check when the system receives the event above
                        // Ex:
                        // Device -> subscribes to srv://MAC@device.rpc.channel
                        // JS Client sends message to Device with a reply to of srv://REPLY_TO_ID@continuum.js.EventBus/replyHandler
                        //
                        // When the system receives the message in the send() handler above it verifies the reply-to matches the sender reply to id
                        // Then we temporarily allow the device to send to the clients reply-to.
                        // Which will allow the message to be routed back to the client.
                        String replyTo = event.metadata().get(EventConstants.REPLY_TO_HEADER);
                        if (replyTo != null) {
                            // wildcard in the reply to are not allowed since they could bypass security constraints
                            if (!replyTo.contains("*")) {
                                session.addTemporarySendAllowed(replyTo);
                            } else {
                                log.warn("reply-to header contains * and will NOT be ALLOWED for message {}",
                                         event);
                            }
                        }
                        subscriptionHandler.handleEvent(event);
                    })
                    .exceptionHandler(subscriptionHandler::handleError);

            subscriptions.put(subscriptionIdentifier, eventConsumer);

            log.debug("New Service Subscription cri: {} id: {} for login: {}",
                      cri.raw(),
                      subscriptionIdentifier,
                      session.participant());


        } else if (cri.scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)) {

            EventConsumer eventConsumer = services.eventStreamService.listen(cri);
            eventConsumer.handler(subscriptionHandler::handleEvent)
                         .exceptionHandler(subscriptionHandler::handleError);

            subscriptions.put(subscriptionIdentifier, eventConsumer);

            log.debug("New Event Subscription cri: {} id: {} for login: {}",
                      cri.raw(),
                      subscriptionIdentifier,
                      session.participant());

        } else {
            throw new IllegalArgumentException("CRI scheme not supported");
        }
    }

    public void unsubscribe(String subscriptionIdentifier) {
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");

        session.touch();

        EventConsumer consumer = subscriptions.remove(subscriptionIdentifier);
        if (consumer != null) {
            consumer.unregister();
        } else {
            log.debug("No subscription exists for subscriptionIdentifier: {}", subscriptionIdentifier);
        }
    }

    private String createConnectedInfoJson(Session session) throws JacksonException {
        ConnectedInfo connectedInfo = new ConnectedInfo(session.participant(), session.replyToId(), session.sessionId());
        return services.jsonMapper.writeValueAsString(connectedInfo);
    }

    private MultiMap createHandshakeResponseHeaders(Session session) {
        MultiMap ret = MultiMap.caseInsensitiveMultiMap();
        if (sessionKeepAliveMode != SessionKeepAliveMode.NONE) {
            ret.add(HttpHeaders.SET_COOKIE, createSessionCookie(EventConstants.SESSION_COOKIE_NAME, session.sessionId(), true));
            ret.add(HttpHeaders.SET_COOKIE, createSessionCookie(EventConstants.SESSION_AVAILABLE_COOKIE_NAME, "true", false));
        }
        return ret;
    }

    private String createSessionCookie(String name, String value, boolean httpOnly) {
        DefaultCookie cookie = new DefaultCookie(name, value);
        cookie.setPath(SESSION_COOKIE_PATH);
        cookie.setMaxAge(TimeUnit.MILLISECONDS.toSeconds(services.kinoticProperties.getSessionTimeout()));
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secureCookies);
        cookie.setSameSite(CookieHeaderNames.SameSite.Lax);
        return ServerCookieEncoder.STRICT.encode(cookie);
    }

    private Map<String, String> toCaseInsensitiveMap(MultiMap headers) {
        Map<String, String> ret = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : headers) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    private String findSessionIdCookie(MultiMap headers) {
        return findCookie(headers, EventConstants.SESSION_COOKIE_NAME);
    }

    private String findCookie(MultiMap headers, String name) {
        for (String cookieHeader : headers.getAll(HttpHeaders.COOKIE)) {
            for (Cookie cookie : ServerCookieDecoder.LAX.decode(cookieHeader)) {
                if (name.equals(cookie.name())) {
                    return cookie.value();
                }
            }
        }
        return null;
    }

    private SessionKeepAliveMode getSessionKeepAliveMode(Map<String, String> connectHeaders) {
        return SessionKeepAliveMode.fromHeader(connectHeaders.get(EventConstants.SESSION_KEEP_ALIVE_HEADER));
    }

    private SessionKeepAliveMode getHandshakeSessionKeepAliveMode(MultiMap headers) {
        return SessionKeepAliveMode.fromHeader(findCookie(headers, EventConstants.SESSION_KEEP_ALIVE_HEADER));
    }

    private boolean isSecureRequest(MultiMap headers) {
        String forwardedProto = headers.get("x-forwarded-proto");
        if ("https".equalsIgnoreCase(forwardedProto)) {
            return true;
        }

        String forwardedSsl = headers.get("x-forwarded-ssl");
        if ("on".equalsIgnoreCase(forwardedSsl)) {
            return true;
        }

        String origin = headers.get(HttpHeaders.ORIGIN);
        return origin != null && origin.startsWith("https://");
    }

    private void startSessionTouchTimer() {
        if (sessionTimer != -1) {
            return;
        }
        long sessionUpdateInterval = services.kinoticProperties.getSessionTimeout() / 2;
        sessionTimer = services.vertx.setPeriodic(sessionUpdateInterval, event -> session.touch());
    }

    private void cancelSessionTimer() {
        if (sessionTimer != -1) {
            services.vertx.cancelTimer(sessionTimer);
            sessionTimer = -1;
        }
    }

    private void validateReplyToForServiceRequest(Event<byte[]> event) {
        String replyTo = event.metadata().get(EventConstants.REPLY_TO_HEADER);
        if (replyTo != null) {
            // reply-to must not use any * characters and must be "scoped" to the participant replyToId
            if (replyTo.contains("*")) {
                throw new IllegalArgumentException("reply-to header invalid * are not allowed for service requests");
            }

            CRI replyCRI;
            try {
                replyCRI = CRI.create(replyTo);
            } catch (Exception e) {
                throw new IllegalArgumentException("reply-to header invalid " + e.getMessage());
            }

            String scheme = replyCRI.scheme();
            if (scheme == null || !scheme.equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {
                throw new IllegalArgumentException("reply-to header invalid, scheme: " + scheme + " is not valid for service requests");
            }

            String scope = replyCRI.scope();
            if (scope != null) {
                // valid scopes are PARTICIPANT-REPLY_TO_ID:UUID or PARTICIPANT-REPLY_TO_ID
                int idx = scope.indexOf(":");
                if (idx != -1) {
                    scope = scope.substring(0, idx);
                }

                if (!scope.equals(session.replyToId())) {
                    throw new IllegalArgumentException("reply-to header invalid, scope: " + scope + " is not valid for service requests");
                }
            } else {
                throw new IllegalArgumentException(
                        "reply-to header invalid, scope: null is not valid for service requests");
            }
        }
        // FIXME: put this back when we fix the reply-to to reply-to problem
//        }else{
//            throw new IllegalArgumentException("reply-to header invalid not provided for service requests");
//        }
    }

}
