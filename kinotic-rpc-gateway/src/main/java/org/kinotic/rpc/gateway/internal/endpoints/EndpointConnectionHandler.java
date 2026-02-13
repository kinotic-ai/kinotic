

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.Promise;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import org.apache.commons.lang3.Validate;
import org.kinotic.rpc.api.exceptions.AuthenticationException;
import org.kinotic.rpc.api.exceptions.AuthorizationException;
import org.kinotic.rpc.api.exceptions.RpcMissingServiceException;
import org.kinotic.rpc.api.security.ConnectedInfo;
import org.kinotic.rpc.api.security.SecurityService;
import org.kinotic.rpc.api.event.CRI;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventConstants;
import org.kinotic.rpc.api.security.Session;
import org.kinotic.rpc.gateway.internal.api.security.CliSecurityService;
import org.kinotic.rpc.internal.utils.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Generic class to perform {@link Event} handling coming from various endpoints
 * <p>
 * <p>
 * Created by Navid Mitchell on 11/3/20
 */
public class EndpointConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(EndpointConnectionHandler.class);
    private final SecurityService securityService;
    private final Services services;
    private final Map<String, BaseSubscriber<Event<byte[]>>> subscriptions = new HashMap<>();
    private Session session;
    private boolean disableStickySession = false;
    private long sessionTimer = -1;

    public EndpointConnectionHandler(Services services) {
        this.services = services;

        if(services.continuumGatewayProperties.isEnableCLIConnections()){
            this.securityService = new CliSecurityService(services.securityService);
        }else{
            this.securityService = services.securityService;
        }
    }

    /**
     * Requests authentication for the given credentials
     * NOTE: By default we keep the session alive after network disconnection, until the connection times out.
     * @param connectHeaders all the headers provided with the CONNECT frame. This will include the login and passcode headers.
     * @return a {@link Promise} completed normally to authenticate or failed to represent a failed authentication
     *         The promise must contain a Map that will provide any additional headers to be returned to the client with the CONNECTED frame
     */
    public CompletableFuture<Map<String, String>> authenticate(Map<String, String> connectHeaders) {

        String sessionId = connectHeaders.get(EventConstants.SESSION_HEADER);

        if(connectHeaders.containsKey(EventConstants.DISABLE_STICKY_SESSION_HEADER)){
            this.disableStickySession = Boolean.parseBoolean(connectHeaders.get(EventConstants.DISABLE_STICKY_SESSION_HEADER));
            if(this.disableStickySession && sessionId != null){
                return CompletableFuture.failedFuture(new AuthenticationException("Session header provided but also requested to disable sticky session, this is not allowed"));
            }
        }

        // Check if session is being used to authenticate
        if (sessionId != null) {
            return services.sessionManager
                    .findSession(sessionId)
                    .handle((session, throwable) -> {
                        if(throwable != null){
                            throw new AuthenticationException("Could not authenticate with the given Session id", throwable);
                        }else{
                            sessionActive(session);
                            return Map.of(EventConstants.CONNECTED_INFO_HEADER, createConnectedInfoJson(session));
                        }
                    });
        } else {

            String replyToId = connectHeaders.containsKey(EventConstants.REPLY_TO_ID_HEADER)
                    ? connectHeaders.get(EventConstants.REPLY_TO_ID_HEADER)
                    : UUID.randomUUID().toString();

            return securityService.authenticate(connectHeaders)
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
                                  .thenCompose(participant -> services.sessionManager.create(participant, replyToId))
                                  .thenApply(session -> {
                                      sessionActive(session);
                                      Map<String, String> ret = new HashMap<>(2,1.5F);
                                      ret.put(EventConstants.CONNECTED_INFO_HEADER, createConnectedInfoJson(session));
                                      return ret;
                                  });
        }
    }

    public void removeSession() {
        // There will not be a session if authentication was not successful, or disableStickySession was requested
        if (session != null) {
            // We remove the session timer here so the timer does not fire after the session is removed
            if (sessionTimer != -1) {
                services.vertx.cancelTimer(sessionTimer);
                sessionTimer = -1;
            }

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

    public Mono<Void> send(Event<byte[]> incomingEvent) {
        Mono<Void> ret;
        if (session.sendAllowed(incomingEvent.cri())) {

            if (incomingEvent.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

                try {

                    // FIXME: when the invocation is local this happens for no reason. If the event stays on the local bus we shouldn't do this..
                    incomingEvent.metadata().put(EventConstants.SENDER_HEADER, services.jsonMapper.writeValueAsString(session.participant()));

                    // make sure reply-to if present is scoped to sender
                    // FIXME: a reply should not need a reply, therefore a replyCri probably should not be a EventConstants.SERVICE_DESTINATION_PREFIX
                    validateReplyToForServiceRequest(incomingEvent);

                    ret = services.eventBusService
                            .sendWithAck(incomingEvent)
                            .onErrorMap(throwable -> { // map errors that occurred because no Service invoker was listening
                                boolean predicateRet = false;
                                if (throwable instanceof ReplyException replyException) {
                                    if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                                        predicateRet = true;
                                    }
                                }
                                return predicateRet;
                            }, RpcMissingServiceException::new)
                            .onErrorResume(throwable -> {
                                try {
                                    Event<byte[]> convertedEvent = services.exceptionConverter.convert(incomingEvent.metadata(), throwable);
                                    // since we don't know the subscription id used by the stomp client for this request we send through the eventbus
                                    services.eventBusService.send(convertedEvent);
                                    return Mono.empty();
                                } catch (Exception ex) {
                                    if(log.isDebugEnabled()){
                                        log.debug("Exception occurred converting exception\n{}",
                                                  EventUtil.toString(incomingEvent, true),
                                                  throwable);
                                    }
                                    return Mono.error(ex);
                                }
                            });

                } catch (Exception e) {
                    ret = Mono.error(e);
                }

            } else if (incomingEvent.cri().scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)) {

                ret = services.eventStreamService.send(incomingEvent);

            } else {
                ret = Mono.error(new IllegalArgumentException("CRI scheme not supported"));
            }
        } else {
            ret = Mono.error(new AuthorizationException("Not Authorized to send to " + incomingEvent.cri()));
        }
        return ret;
    }

    public void shutdown() {
        // if session says not to keep alive we shutdown completely, i.e. disable sticky session
        if(this.disableStickySession){
            removeSession();
        }else{
            if (sessionTimer != -1) {
                services.vertx.cancelTimer(sessionTimer);
                sessionTimer = -1;
            }
            subscriptions.forEach((s, messageConsumer) -> messageConsumer.cancel());
            subscriptions.clear();
        }
    }

    public void subscribe(CRI cri, String subscriptionIdentifier, BaseSubscriber<Event<byte[]>> subscriber) {
        Validate.notNull(cri, "CRI must not be null");
        Validate.notEmpty(subscriptionIdentifier, "subscriptionIdentifier must not be empty");
        Validate.notNull(subscriber, "Subscriber must not be null");

        if (!session.subscribeAllowed(cri)) {
            throw new AuthorizationException("Not Authorized to subscribe to " + cri);
        }

        if (cri.scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)) {

            services.eventBusService.listen(cri.baseResource())
                                    .doOnNext(event -> {
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
                                    }) // services we want to make sure reply addresses are implicitly allowed
                                    .subscribe(subscriber);

            subscriptions.put(subscriptionIdentifier, subscriber);

            log.debug("New Service Subscription cri: {} id: {} for login: {}",
                      cri.raw(),
                      subscriptionIdentifier,
                      session.participant());


        } else if (cri.scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)) {

            services.eventStreamService.listen(cri).subscribe(subscriber);

            subscriptions.put(subscriptionIdentifier, subscriber);

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

        if (subscriptions.containsKey(subscriptionIdentifier)) {
            subscriptions.remove(subscriptionIdentifier).cancel();
        } else {
            log.debug("No subscription exists for subscriptionIdentifier: {}", subscriptionIdentifier);
        }
    }

    private String createConnectedInfoJson(Session session){
        try {
            ConnectedInfo connectedInfo = new ConnectedInfo(session.participant(), session.replyToId(), session.sessionId());
            return services.jsonMapper.writeValueAsString(connectedInfo);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sessionActive(Session session) {
        this.session = session;
        // update session at least every half the time of the timeout
        long sessionUpdateInterval = services.kinoticProperties.getSessionTimeout() / 2;
        sessionTimer = services.vertx.setPeriodic(sessionUpdateInterval, event -> this.session.touch());
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
