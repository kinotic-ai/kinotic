


package org.kinotic.core.internal.api.service.invoker;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.*;
import org.kinotic.core.api.exceptions.RpcMissingMethodException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.service.ServiceDescriptor;
import org.kinotic.core.api.service.ServiceFunction;
import org.kinotic.core.api.service.ServiceFunctionInstanceProvider;
import org.kinotic.core.internal.api.event.MetadataTextMapGetter;
import org.kinotic.core.internal.api.service.ExceptionConverter;
import org.kinotic.core.internal.config.KinoticVertxConfig;
import org.kinotic.core.internal.utils.EventUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class handles invoking services that are published to the Continuum.
 *
 *
 * Created by Navid Mitchell on 2019-03-20.
 */
public class ServiceInvocationSupervisor {

    private static final Logger log = LoggerFactory.getLogger(ServiceInvocationSupervisor.class);

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, StreamSubscriber> activeStreamingResults = new ConcurrentHashMap<>();
    private final MetadataTextMapGetter textMapGetter = new MetadataTextMapGetter();
    private final ArgumentResolver argumentResolver;
    private final EventBusService eventBusService;
    private final ExceptionConverter exceptionConverter;
    private final JsonMapper jsonMapper;
    private final Map<String, HandlerMethod> methodMap;
    private final SecurityContext securityContext;
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final ReturnValueConverter returnValueConverter;
    private final ServiceDescriptor serviceDescriptor;
    private final Vertx vertx;
    private final OpenTelemetry openTelemetry;


    private EventConsumer methodInvocationEventConsumer;



    public ServiceInvocationSupervisor(ServiceDescriptor serviceDescriptor,
                                       ServiceFunctionInstanceProvider instanceProvider,
                                       ArgumentResolver argumentResolver,
                                       ReturnValueConverter returnValueConverter,
                                       ExceptionConverter exceptionConverter,
                                       EventBusService eventBusService,
                                       ReactiveAdapterRegistry reactiveAdapterRegistry,
                                       Vertx vertx,
                                       OpenTelemetry openTelemetry,
                                       JsonMapper jsonMapper,
                                       SecurityContext securityContext) {

        Validate.notNull(serviceDescriptor, "ServiceDescriptor must not be null");
        Validate.notNull(instanceProvider, "ServiceFunctionInstanceProvider must not be null");
        Validate.notNull(argumentResolver, "argumentResolver must not be null");
        Validate.notNull(returnValueConverter, "returnValueConverter must not be null");
        Validate.notNull(exceptionConverter, "exceptionConverter must not be null");
        Validate.notNull(eventBusService, "eventBusService must not be null");
        Validate.notNull(reactiveAdapterRegistry, "reactiveAdapterRegistry must not be null");
        Validate.notNull(vertx, "vertx must not be null");
        Validate.notNull(openTelemetry, "OpenTelemetry must not be null");
        Validate.notNull(jsonMapper, "jsonMapper must not be null");
        Validate.notNull(securityContext, "securityContext must not be null");

        this.serviceDescriptor = serviceDescriptor;
        this.argumentResolver = argumentResolver;
        this.returnValueConverter = returnValueConverter;
        this.exceptionConverter = exceptionConverter;
        this.eventBusService = eventBusService;
        this.jsonMapper = jsonMapper;
        this.securityContext = securityContext;
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
        this.vertx = vertx;
        this.openTelemetry = openTelemetry;

        this.methodMap = buildMethodMap(serviceDescriptor, instanceProvider);
    }

    public boolean isActive(){
        return active.get();
    }

    /**
     * Starts this {@link ServiceInvocationSupervisor}
     * @return a Future that will succeed on Start and fail on an error
     */
    public Future<Void> start(){
        if(active.compareAndSet(false, true)){
            // begin listening on the event bus for service invocation requests
            methodInvocationEventConsumer = eventBusService.listen(serviceDescriptor.serviceIdentifier().cri().baseResource());

            methodInvocationEventConsumer
                    .handler(event -> vertx.executeBlocking(() -> {
                        processEvent(event);
                        return null;
                    }))
                    .exceptionHandler(throwable -> log.error("Event listener error", throwable))
                    .endHandler(v -> {
                        log.error("Should not happen! Event listener stopped for some reason!! Changing supervisor state to inactive");
                        active.set(false);
                    });

            return methodInvocationEventConsumer.completion();
        }else{
            return Future.failedFuture(new IllegalStateException("Service already started"));
        }
    }

    /**
     * Stops this {@link ServiceInvocationSupervisor}
     * @return a Future that will succeed on Stop and fail on an error
     */
    public Future<Void> stop(){
        if (active.compareAndSet(true, false)) {
            for(Map.Entry<String, StreamSubscriber> streamSubscribers : activeStreamingResults.entrySet()){
                streamSubscribers.getValue().cancel();
            }

            if(methodInvocationEventConsumer != null){
                return methodInvocationEventConsumer.unregister();
            }
            return Future.succeededFuture();
        }else{
            return Future.failedFuture(new IllegalStateException("Service already stopped"));
        }
    }

    private Map<String, HandlerMethod> buildMethodMap(ServiceDescriptor serviceDescriptor,
                                                      ServiceFunctionInstanceProvider instanceProvider) {
        final HashMap<String, HandlerMethod> ret = new HashMap<>();

        for(ServiceFunction serviceFunction : serviceDescriptor.functions()){
            Object instance = instanceProvider.provideInstance(serviceFunction);
            Method specificMethod = AopUtils.selectInvocableMethod(serviceFunction.invocationMethod(), instance.getClass());

            // add a / since uri paths contain this
            String methodName = "/" + specificMethod.getName();

            if(ret.containsKey(methodName)){
                throw new IllegalArgumentException("Multiple ServiceFunctions provided with the name " + specificMethod.getName());
            }else{
                HandlerMethod handlerMethod = new HandlerMethod(instance, specificMethod);
                ret.put(methodName,  handlerMethod);
            }
        }
        return ret;
    }

    private void convertAndSend(Metadata incomingMetadata, HandlerMethod handlerMethod, Object result) {
        try {
            Event<byte[]> resultEvent = returnValueConverter.convert(incomingMetadata,
                                                                     handlerMethod.getReturnType()
                                                                                  .getParameterType(),
                                                                     result);
            eventBusService.send(resultEvent);
        } catch (Exception e) {
            if(log.isDebugEnabled()){
                log.debug("Exception occurred sending response", e);
            }
            throw e;
        }
    }

    private void handleException(Metadata incomingMetadata, Throwable e) {
        try {
            Event<byte[]> convertedEvent = exceptionConverter.convert(incomingMetadata, e);
            eventBusService.send(convertedEvent);
        } catch (Exception ex) {
            log.error("Error occurred when calling exception converter",e);
        }
    }

    private void processControlPlaneRequest(Event<byte[]> incomingEvent){
        // All control plane requests require a CORRELATION_ID_HEADER to know what long-running request is being referenced
        String correlationId = incomingEvent.metadata().get(EventConstants.CORRELATION_ID_HEADER);
        Validate.notNull(correlationId, "Streaming control plain messages require a CORRELATION_ID_HEADER to be set");

        activeStreamingResults.computeIfPresent(correlationId, (s, streamSubscriber) -> {
            streamSubscriber.processControlEvent(incomingEvent);
            return streamSubscriber;
        });
    }

    private void processEvent(Event<byte[]> incomingEvent){
        boolean isControl = incomingEvent.metadata().contains(EventConstants.CONTROL_HEADER);

        log.trace("Service {} requested for {}", isControl ? "Control" : "Invocation", incomingEvent.cri());

        if(exceptionConverter.supports(incomingEvent.metadata())) {
            try {

                // Ensure all headers needed after processing are available
                Validate.isTrue(incomingEvent.cri().hasPath(), "The methodId must not be blank");

                // See if we are dealing with a control plane message or a regular invocation request
                if(isControl){
                    processControlPlaneRequest(incomingEvent);
                }else{
                    if(validateReplyTo(incomingEvent)){
                        processInvocationRequest(incomingEvent);
                    }else{
                        log.error("ReplyTo header is missing or invalid incoming message will be ignored\n{}", EventUtil.toString(
                                incomingEvent,
                                true));
                    }
                }


            } catch (Exception e) {
                log.debug("Exception occurred processing service request\n{}", EventUtil.toString(incomingEvent, true), e);
                handleException(incomingEvent.metadata(), e);
            }
        }else{ // no exception converter found we will not execute message since we can not deal with an exception
            log.error("No exception converter found incoming message will be ignored");
        }
    }

    private void processInvocationRequest(Event<byte[]> incomingEvent) {

        // Ensure there is an argument resolver that can handle the incoming data
        if (argumentResolver.supports(incomingEvent)) {

                // Resolve arguments based on handler method and incoming data
                HandlerMethod handlerMethod = methodMap.get(incomingEvent.cri().path());
                if(handlerMethod == null){
                    throw new RpcMissingMethodException("No method could be resolved for methodId " + incomingEvent.cri().path());
                }

                if (!returnValueConverter.supports(incomingEvent.metadata(),
                                                   handlerMethod.getReturnType().getParameterType())) {
                    throw new IllegalStateException("No compatible ReturnValueConverter found");
                }

                // Inject the Participant into the Vert.x context so service methods can access it via context.getLocal()
                String participantJson = incomingEvent.metadata().get(EventConstants.SENDER_HEADER);
                if (participantJson != null) {
                    try {
                        Participant participant = jsonMapper.readValue(participantJson, Participant.class);
                        Context context = Vertx.currentContext();
                        if (context != null) {
                            securityContext.setParticipant(context, participant);
                        }
                    } catch (JacksonException e) {
                        log.warn("Failed to deserialize Participant from event metadata", e);
                    }
                }

                Object[] arguments = argumentResolver.resolveArguments(incomingEvent, handlerMethod);

                // separate try catch since we do not want to log invocation errors
                Object result = null;
                boolean error = false;
                try {
                    // Invoke the method and then handle the result
                    result = handlerMethod.invoke(arguments);
                } catch (Exception e) {
                    error = true;
                    handleException(incomingEvent.metadata(), e);
                }

                if (!error) {
                    processMethodInvocationResult(incomingEvent, handlerMethod, result);
                }

        } else {
            throw new IllegalStateException("No compatible ArgumentResolver found");
        }
    }

    private void processMethodInvocationResult(Event<byte[]> incomingEvent, HandlerMethod handlerMethod, Object result){

        Metadata incomingMetadata = incomingEvent.metadata();

        // Check if result is reactive if so we only complete once result is complete
        ReactiveAdapter reactiveAdapter = reactiveAdapterRegistry.getAdapter(null, result);
        if(reactiveAdapter == null){

            convertAndSend(incomingMetadata, handlerMethod, result);

        }else{

            if(!reactiveAdapter.isMultiValue()){

                Publisher<?> publisher = reactiveAdapter.toPublisher(result);
                publisher.subscribe(new SingleValueSubscriber(incomingMetadata, handlerMethod, incomingEvent));

            }else{

                // All long-running results require a CORRELATION_ID_HEADER to be able to coordinate with the requester
                if(!incomingEvent.metadata().contains(EventConstants.CORRELATION_ID_HEADER)){
                    throw new IllegalArgumentException("Streaming results require a CORRELATION_ID_HEADER to be set");
                }

                String correlationId = incomingEvent.metadata().get(EventConstants.CORRELATION_ID_HEADER);
                activeStreamingResults.computeIfAbsent(correlationId, s -> {
                    //  FIXME: logic error here clients like the js client will stay alive during multiple requests even though previous request was invalidated indirectly
                    Flux<?> flux = Flux.from(reactiveAdapter.toPublisher(result));

                    CRI replyCRI = CRI.create(incomingEvent.metadata().get(EventConstants.REPLY_TO_HEADER));
                    Flux<ListenerStatus> replyListenerStatus = eventBusService.monitorListenerStatus(replyCRI.baseResource());

                    StreamSubscriber streamSubscriber = new StreamSubscriber(incomingMetadata, handlerMethod, replyListenerStatus);
                    flux.subscribe(streamSubscriber);
                    return streamSubscriber;
                });
            }
        }
    }

    private void sendCompletionEvent(Metadata incomingMetadata){
        Event<byte[]> completionEvent = EventUtil.createReplyEvent(incomingMetadata,
                                                                   Map.of(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_COMPLETE),
                                                                   null);
        eventBusService.send(completionEvent);
    }

    private boolean validateReplyTo(Event<byte[]> incomingEvent){
        boolean ret = false;
        String replyTo = incomingEvent.metadata().get(EventConstants.REPLY_TO_HEADER);
        if(replyTo != null){
            if(!replyTo.isBlank()) {
                if (replyTo.startsWith(EventConstants.SERVICE_DESTINATION_SCHEME + ":")) {
                    ret = true;
                } else {
                    log.warn("Reply-to header must be a valid service destination");
                }
            }else {
                log.warn("Reply-to header must not be blank");
            }
        }else {
            log.warn("No reply-to header found in event");
        }
        return ret;
    }

    /**
     * Subscriber that handles a single-value reactive result from a method invocation.
     * Replaces the previous Mono.from() pattern.
     */
    private class SingleValueSubscriber implements Subscriber<Object> {

        private final Metadata incomingMetadata;
        private final HandlerMethod handlerMethod;
        private final Event<byte[]> incomingEvent;
        private boolean valueReceived = false;

        public SingleValueSubscriber(Metadata incomingMetadata, HandlerMethod handlerMethod, Event<byte[]> incomingEvent) {
            this.incomingMetadata = incomingMetadata;
            this.handlerMethod = handlerMethod;
            this.incomingEvent = incomingEvent;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(Object value) {
            valueReceived = true;
            convertAndSend(incomingMetadata, handlerMethod, value);
        }

        @Override
        public void onError(Throwable t) {
            if(log.isDebugEnabled()){
                log.debug("Exception occurred processing service request\n{}",
                          EventUtil.toString(incomingEvent, true),
                          t);
            }
            handleException(incomingMetadata, t);
        }

        @Override
        public void onComplete() {
            if(!valueReceived){
                convertAndSend(incomingMetadata, handlerMethod, null);
            }
        }
    }

    /**
     * This subscriber handles monitoring the remote ends subscription for reply events.
     * If it detects that the remote ends subscription for reply events is removed, it will terminate the {@link StreamSubscriber}
     */
    private static class ReplyListenerStatusSubscriber extends BaseSubscriber<ListenerStatus> {
        private final StreamSubscriber streamSubscription;

        public ReplyListenerStatusSubscriber(StreamSubscriber streamSubscription) {
            this.streamSubscription = streamSubscription;
        }

        @Override
        protected void hookOnComplete() {
            // This condition should not occur under normal operation
            log.error("Reply Listener Monitor completed for some reason! Terminating streaming result.");
            streamSubscription.cancel();
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            // This condition should not occur under normal operation
            log.error("Reply Listener Monitor threw an exception. Terminating streaming result.", throwable);
            streamSubscription.cancel();
        }

        @Override
        protected void hookOnNext(ListenerStatus status) {
            if(log.isTraceEnabled()){
                log.trace("Received ListenerStatus {}", status);
            }
            // TODO: handle resume restart type logic
            if(status == ListenerStatus.INACTIVE){
                if(!streamSubscription.isDisposed()) {
                    log.trace("No more listeners active terminating streaming result.");
                    streamSubscription.cancel();
                    // ReplyListenerStatusSubscriber will be canceled by the streamSubscription
                }
            }
        }
    }

    /**
     * This subscriber will handle processing for any {@link org.reactivestreams.Publisher} returned by a method invocation
     * It may be acted on by the remote end by sending control requests to this supervisor
     */
    private class StreamSubscriber extends BaseSubscriber<Object> {

        private final HandlerMethod handlerMethod;
        private final Metadata incomingMetadata;
        private final Flux<ListenerStatus> replyListenerStatus;
        private ReplyListenerStatusSubscriber replyListenerStatusSubscriber;

        public StreamSubscriber(Metadata incomingMetadata,
                                HandlerMethod handlerMethod,
                                Flux<ListenerStatus> replyListenerStatus) {
            this.incomingMetadata = incomingMetadata;
            this.handlerMethod = handlerMethod;
            this.replyListenerStatus = replyListenerStatus;
        }

        public void processControlEvent(Event<byte[]> incomingEvent){
            String control = incomingEvent.metadata().get(EventConstants.CONTROL_HEADER);
            if(log.isTraceEnabled()){
                log.trace("Processing control event {}", control);
            }
            switch (control) {
                case EventConstants.CONTROL_VALUE_CANCEL:
                    this.cancel();
                    break;
                case EventConstants.CONTROL_VALUE_SUSPEND:
                    this.request(0);
                    break;
                case EventConstants.CONTROL_VALUE_RESUME:
                    this.requestUnbounded();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown control header value " + control);
            }
        }

        @Override
        protected void hookFinally(SignalType type) {
            log.trace("Stream Cleanup Now");

            replyListenerStatusSubscriber.cancel();

            String correlationId = incomingMetadata.get(EventConstants.CORRELATION_ID_HEADER);
            // we must do this in a background thread since if the flux is created like Flux.just this will be executed in the same thread as the invocation
            // and hence inside the activeStreamingResults.computeIfAbsent block
            vertx.executeBlocking(() -> {
                activeStreamingResults.remove(correlationId);
                return null;
            });
        }

        @Override
        protected void hookOnComplete() {
            log.trace("Stream Complete");
            sendCompletionEvent(incomingMetadata);
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            if(log.isTraceEnabled()){
                log.trace("Stream Error",throwable);
            }
            handleException(incomingMetadata, throwable);
        }

        @Override
        protected void hookOnNext(Object value) {
            if(log.isTraceEnabled()){
                log.trace("Next stream value {}", value);
            }
            convertAndSend(incomingMetadata, handlerMethod, value);
        }

        @Override
        protected void hookOnSubscribe(Subscription subscription) {

            replyListenerStatusSubscriber = new ReplyListenerStatusSubscriber(this);
            replyListenerStatus.subscribe(replyListenerStatusSubscriber);

            super.hookOnSubscribe(subscription);
        }

    }

}
