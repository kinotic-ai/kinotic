


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
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.api.CliSecurityService;
import org.kinotic.gateway.internal.endpoints.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navid Mitchell on 11/3/20
 */
public class EndpointConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(EndpointConnectionHandler.class);
    private static final String CONNECTED_INFO_SESSION_KEY = EndpointConnectionHandler.class.getName() + ".connectedInfo";
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

        if(services.apiGatewayProperties.isEnableCLIConnections()){
            this.securityService = new CliSecurityService(services.securityService);
        }else{
            this.securityService = services.securityService;
        }
    }

    public CompletableFuture<MultiMap> handshake(RoutingContext routingContext) {
        session = routingContext.session();
        this.connectedInfo = connectedInfoFromSession();

        if (connectedInfo != null && connectedInfo.getParticipant() != null) {
            return CompletableFuture.completedFuture(MultiMap.caseInsensitiveMultiMap());
        }

        return securityService.authenticate(toCaseInsensitiveMap(routingContext.request().headers()))
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
                              .thenApply(participant -> {
                                  connectedInfo = new ConnectedInfo();
                                  connectedInfo.setParticipant(participant);
                                  if (session != null) {
                                      session.put(CONNECTED_INFO_SESSION_KEY, connectedInfo);
                                  }
                                  return MultiMap.caseInsensitiveMultiMap();
                              });
    }

    public CompletableFuture<Map<String, String>> connect(Map<String, String> connectHeaders) {
        if (connectedInfo != null && connectedInfo.getParticipant() != null) {
            try {
                sessionKeepAliveMode = SessionKeepAliveMode.fromHeader(connectHeaders.get(EventConstants.SESSION_KEEP_ALIVE_HEADER));
                if (sessionKeepAliveMode != SessionKeepAliveMode.NONE && session == null) {
                    throw new AuthenticationException("A Vert.x session is required unless session keep alive mode is NONE");
                }

                String replyToId = connectHeaders.get(EventConstants.REPLY_TO_ID_HEADER);
                Validate.notEmpty(replyToId, "Client must provide a reply-to-id header");
                connectedInfo.setReplyToId(replyToId);
                if (session != null) {
                    session.put(CONNECTED_INFO_SESSION_KEY, connectedInfo);
                }
                stompAuthorizer = services.stompAuthorizerFactory.create(connectedInfo);

                touchSession();
                if (sessionKeepAliveMode == SessionKeepAliveMode.CONNECTION) {
                    startSessionTouchTimer();
                }
                return CompletableFuture.completedFuture(Map.of(EventConstants.CONNECTED_INFO_HEADER,
                                                                services.jsonMapper.writeValueAsString(connectedInfo)));
            } catch (JacksonException e) {
                return CompletableFuture.failedFuture(e);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(new AuthenticationException("Invalid CONNECT frame", e));
            }
        }

        return CompletableFuture.failedFuture(new AuthenticationException("Client must authenticate before sending a CONNECT frame"));
    }

    public void removeSession() {
        if (sessionKeepAliveMode == SessionKeepAliveMode.NONE && session != null) {
            session.destroy();
        }
    }

    public Future<Void> send(Event<byte[]> incomingEvent) {
        touchSession();

        if (!stompAuthorizer.sendAllowed(incomingEvent.cri())) {
            return Future.failedFuture(new AuthorizationException("Not Authorized to send to " + incomingEvent.cri()));
        }

        if (incomingEvent.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            try {

                // FIXME: when the invocation is local this happens for no reason. If the event stays on the local bus we shouldn't do this..
                incomingEvent.metadata().put(EventConstants.SENDER_HEADER, services.jsonMapper.writeValueAsString(connectedInfo.getParticipant()));

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

        touchSession();

        if (!stompAuthorizer.subscribeAllowed(cri)) {
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

        } else {
            throw new IllegalArgumentException("CRI scheme not supported");
        }
    }

    public void unsubscribe(String subscriptionIdentifier) {
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");

        touchSession();

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
        Object value = session.get(CONNECTED_INFO_SESSION_KEY);
        if (value instanceof ConnectedInfo storedConnectedInfo) {
            return storedConnectedInfo;
        }
        return null;
    }

    private void touchSession() {
        if (sessionKeepAliveMode == SessionKeepAliveMode.ACTIVITY && session != null) {
            session.setAccessed();
        }
    }

    private void startSessionTouchTimer() {
        if (sessionTimer != -1) {
            return;
        }
        long sessionUpdateInterval = services.apiGatewayProperties.getSessionTimeout() / 2;
        sessionTimer = services.vertx.setPeriodic(sessionUpdateInterval, event -> {
            if (session != null) {
                session.setAccessed();
            }
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

                if (!scope.equals(connectedInfo.getReplyToId())) {
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
