


package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.endpoints.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Created by Navid Mitchell on 11/3/20
 */
public class EndpointConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(EndpointConnectionHandler.class);
    private final SecurityService securityService;
    private final Services services;
    private final Map<String, EventConsumer> subscriptions = new HashMap<>();
    private Session session;
    private ConnectedInfo connectedInfo;
    private StompAuthorizer stompAuthorizer;
    private SessionKeepAliveMode sessionKeepAliveMode = SessionKeepAliveMode.ACTIVITY;
    private long sessionTimer = -1;

    public EndpointConnectionHandler(Services services) {
        this.services = services;
        this.securityService = services.securityService;
    }

    public Future<MultiMap> handshake(RoutingContext routingContext) {
        session = routingContext.session();
        this.connectedInfo = connectedInfoFromSession();

        if (connectedInfo != null && connectedInfo.getParticipant() != null) {
            return Future.succeededFuture(MultiMap.caseInsensitiveMultiMap());
        }

        return securityService.authenticate(toCaseInsensitiveMap(routingContext.request().headers()))
                              .recover(throwable -> {
                                  Throwable cause;
                                  if(throwable instanceof AuthenticationException){
                                      cause = throwable;
                                  }else{
                                      cause = new AuthenticationException("Could not authenticate with the given credentials", throwable);
                                  }
                                  return Future.failedFuture(cause);
                              })
                              .map(participant -> {
                                  connectedInfo = new ConnectedInfo();
                                  connectedInfo.setParticipant(participant);
                                  if (session != null) {
                                      session.put(ConnectedInfo.SESSION_KEY, connectedInfo);
                                  }
                                  return MultiMap.caseInsensitiveMultiMap();
                              });
    }

    public Future<Map<String, String>> connect(Map<String, String> connectHeaders) {
        if (connectedInfo == null || connectedInfo.getParticipant() == null) {
            return Future.failedFuture(new AuthenticationException("Client must authenticate before sending a CONNECT frame"));
        }

        Future<Map<String, String>> ret;
        try {
            sessionKeepAliveMode = SessionKeepAliveMode.fromHeader(connectHeaders.get(EventConstants.SESSION_KEEP_ALIVE_HEADER));
            if (sessionKeepAliveMode != SessionKeepAliveMode.NONE && session == null) {
                ret = Future.failedFuture(
                        new AuthenticationException("A Vert.x session is required unless session keep alive mode is NONE"));
            } else {
                // The replyToId is generated server side so the client cannot pick a guessable
                // or colliding value. It is reused for the life of the session so the client's
                // reply destination stays stable across reconnects.
                if (connectedInfo.getReplyToId() == null) {
                    connectedInfo.setReplyToId(UUID.randomUUID().toString());
                }
                if (session != null) {
                    session.put(ConnectedInfo.SESSION_KEY, connectedInfo);
                }
                stompAuthorizer = services.stompAuthorizerFactory.create(connectedInfo);

                signalActivity();
                if (sessionKeepAliveMode == SessionKeepAliveMode.CONNECTION) {
                    startSessionTouchTimer();
                }
                ret = Future.succeededFuture(Map.of(EventConstants.CONNECTED_INFO_HEADER,
                                                    services.jsonMapper.writeValueAsString(connectedInfo)));
            }
        } catch (JacksonException e) {
            ret = Future.failedFuture(e);
        } catch (IllegalArgumentException e) {
            ret = Future.failedFuture(new AuthenticationException("Invalid CONNECT frame", e));
        }
        return ret;
    }

    public void removeSession() {
        if (sessionKeepAliveMode == SessionKeepAliveMode.NONE && session != null) {
            session.destroy();
        }
    }

    public Future<Void> send(Event<byte[]> incomingEvent) {
        signalActivity();

        if (!stompAuthorizer.sendAllowed(incomingEvent.cri())) {
            return Future.failedFuture(new AuthorizationException("Not Authorized to send to " + incomingEvent.cri()));
        }

        if (incomingEvent.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            try {

                incomingEvent.setSender(connectedInfo.getParticipant());

                // make sure reply-to if present is scoped to sender
                validateReplyToForServiceRequest(incomingEvent);

                return services.eventBusService
                        .sendWithAck(incomingEvent)
                        .recover(throwable -> {
                            // map errors that occurred because no Service invoker was listening
                            if (throwable instanceof ReplyException replyException) {
                                if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                                    // every gateway RPC doubles as a liveness probe, so the directory
                                    // self-heals from ordinary traffic; with no directory bean nothing happens
                                    ServiceDirectory serviceDirectory = services.serviceDirectoryProvider.getIfAvailable();
                                    if (serviceDirectory != null) {
                                        serviceDirectory.reportUnreachable(incomingEvent.cri().raw());
                                    }
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

        } else if (incomingEvent.cri().scheme().equals(EventConstants.REPLY_DESTINATION_SCHEME)) {

            // A reply is a one-way delivery to the requester's reply destination. It is never
            // invoked and never itself replies, so no ack and no reply-to validation apply.
            services.eventBusService.send(incomingEvent);
            return Future.succeededFuture();

        } else {
            return Future.failedFuture(new IllegalArgumentException("CRI scheme not supported"));
        }
    }

    public void shutdown() {
        if (sessionTimer != -1) {
            services.vertx.cancelTimer(sessionTimer);
            sessionTimer = -1;
        }
        subscriptions.forEach((s, eventConsumer) -> eventConsumer.unregister());
        subscriptions.clear();
        session = null;
        connectedInfo = null;
        stompAuthorizer = null;
    }

    public void subscribe(CRI cri,
                          String subscriptionIdentifier,
                          StompSubscriptionHandler subscriptionHandler) {
        Validate.notNull(cri, "CRI must not be null");
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");
        Validate.notNull(subscriptionHandler, "subscriptionHandler must not be null");

        signalActivity();

        if (!stompAuthorizer.subscribeAllowed(cri)) {
            throw new AuthorizationException("Not Authorized to subscribe to " + cri);
        }

        if (cri.scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            EventConsumer eventConsumer = services.eventBusService.listen(cri);
            eventConsumer.handler(event -> {
                        // If reply-to is set we implicitly allow the subscriber to send a single message to the given destination
                        // Reply-To is known to be scoped to the sender because there is a check when the system receives the event above
                        // Ex:
                        // Device -> subscribes to srv://MAC@device.rpc.channel
                        // JS Client sends message to Device with a reply to of reply://REPLY_TO_ID@continuum.js.EventBus/replyHandler
                        //
                        // When the system receives the message in the send() handler above it verifies the reply-to matches the sender reply to id
                        // Then we temporarily allow the device to send to the clients reply-to.
                        // Which will allow the message to be routed back to the client.
                        String replyTo = event.metadata().get(EventConstants.REPLY_TO_HEADER);
                        if (replyTo != null) {
                            // wildcard in the reply to are not allowed since they could bypass security constraints
                            if (!replyTo.contains("*")) {
                                stompAuthorizer.addTemporarySendAllowed(replyTo);
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
                      connectedInfo.getParticipant());


        } else if (cri.scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)) {

            EventConsumer eventConsumer = services.eventStreamService.listen(cri);
            eventConsumer.handler(subscriptionHandler::handleEvent)
                         .exceptionHandler(subscriptionHandler::handleError);

            subscriptions.put(subscriptionIdentifier, eventConsumer);

            log.debug("New Event Subscription cri: {} id: {} for login: {}",
                      cri.raw(),
                      subscriptionIdentifier,
                      connectedInfo.getParticipant());

        } else if (cri.scheme().equals(EventConstants.REPLY_DESTINATION_SCHEME)) {

            EventConsumer eventConsumer = services.eventBusService.listen(cri);
            eventConsumer.handler(subscriptionHandler::handleEvent)
                         .exceptionHandler(subscriptionHandler::handleError);

            subscriptions.put(subscriptionIdentifier, eventConsumer);

            log.debug("New Reply Subscription cri: {} id: {} for login: {}",
                      cri.raw(),
                      subscriptionIdentifier,
                      connectedInfo.getParticipant());

        } else {
            throw new IllegalArgumentException("CRI scheme not supported");
        }
    }

    public void unsubscribe(String subscriptionIdentifier) {
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");

        signalActivity();

        EventConsumer consumer = subscriptions.remove(subscriptionIdentifier);
        if (consumer != null) {
            consumer.unregister();
        } else {
            log.debug("No subscription exists for subscriptionIdentifier: {}", subscriptionIdentifier);
        }
    }

    private Map<String, String> toCaseInsensitiveMap(MultiMap headers) {
        Map<String, String> ret = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : headers) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    private ConnectedInfo connectedInfoFromSession() {
        if (session == null) {
            return null;
        }
        Object value = session.get(ConnectedInfo.SESSION_KEY);
        if (value instanceof ConnectedInfo storedConnectedInfo) {
            return storedConnectedInfo;
        }
        return null;
    }

    private void signalActivity() {
        if (sessionKeepAliveMode == SessionKeepAliveMode.ACTIVITY) {
            if (session == null) {
                log.error("Session is null while sessionKeepAliveMode is ACTIVITY");
                throw new IllegalStateException("Internal server error");
            }
            session.setAccessed();
        }
    }

    private void startSessionTouchTimer() {
        if (sessionTimer != -1) {
            log.error("Session-touch timer already started");
            throw new IllegalStateException("Internal server error");
        }
        long sessionUpdateInterval = services.apiGatewayProperties.getSessionTimeout() / 2;
        sessionTimer = services.vertx.setPeriodic(sessionUpdateInterval, event -> {
            if (session == null) {
                log.error("Session is null while session-touch timer is active");
                throw new IllegalStateException("Internal server error");
            }
            session.setAccessed();
        });
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
            if (scheme == null || !scheme.equals(EventConstants.REPLY_DESTINATION_SCHEME)) {
                throw new IllegalArgumentException("reply-to header invalid, scheme: " + scheme + " is not valid for service requests");
            }

            String scope = replyCRI.scope();
            if (scope != null) {
                // valid scopes are PARTICIPANT-REPLY_TO_ID:UUID or PARTICIPANT-REPLY_TO_ID
                int idx = scope.indexOf(":");
                if (idx != -1) {
                    scope = scope.substring(0, idx);
                }

                if (!scope.equals(connectedInfo.getReplyToId())) {
                    throw new IllegalArgumentException("reply-to header invalid, scope: " + scope + " is not valid for service requests");
                }
            } else {
                throw new IllegalArgumentException(
                        "reply-to header invalid, scope: null is not valid for service requests");
            }
        } else {
            throw new IllegalArgumentException("reply-to header invalid, not provided for service request");
        }
    }

}
