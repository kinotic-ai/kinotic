package org.kinotic.core.internal.api.service.invoker;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.NonNull;
import org.kinotic.core.api.annotations.ScopeOptional;
import org.kinotic.core.api.event.*;
import org.kinotic.core.api.exceptions.RpcInvocationException;
import org.kinotic.core.api.exceptions.RpcMissingMethodException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.service.ServiceDescriptor;
import org.kinotic.core.api.service.FunctionDescriptor;
import org.kinotic.core.api.service.FunctionInstanceProvider;
import org.kinotic.core.internal.api.service.ExceptionConverter;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.core.internal.utils.TelemetryUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private static final String INSTRUMENTATION_NAME = "org.kinotic.core.service-invoker";

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, StreamSubscriber> activeStreamingResults = new ConcurrentHashMap<>();
    private final ArgumentResolver argumentResolver;
    private final EventBusService eventBusService;
    private final ExceptionConverter exceptionConverter;
    private final Map<String, HandlerMethod> methodMap;
    private final Set<String> scopeOptionalMethodIds;
    private final SecurityContext securityContext;
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final ReturnValueConverter returnValueConverter;
    private final ServiceDescriptor serviceDescriptor;
    private final TextMapPropagator propagator;
    private final TraceLogFilter traceLogFilter;
    private final Tracer tracer;
    private final Vertx vertx;


    // Consumer for the address the service is addressable by
    private EventConsumer methodInvocationEventConsumer;

    // Present only for a scoped service with ScopeOptional methods: the shared unscoped
    // address every instance listens on for the methods any instance can answer
    private EventConsumer unscopedInvocationEventConsumer;


    public ServiceInvocationSupervisor(ServiceDescriptor serviceDescriptor,
                                       FunctionInstanceProvider instanceProvider,
                                       ArgumentResolver argumentResolver,
                                       ReturnValueConverter returnValueConverter,
                                       ExceptionConverter exceptionConverter,
                                       EventBusService eventBusService,
                                       ReactiveAdapterRegistry reactiveAdapterRegistry,
                                       Vertx vertx,
                                       SecurityContext securityContext,
                                       OpenTelemetry openTelemetry,
                                       TraceLogFilter traceLogFilter) {

        Validate.notNull(serviceDescriptor, "ServiceDescriptor must not be null");
        Validate.notNull(instanceProvider, "FunctionInstanceProvider must not be null");
        Validate.notNull(argumentResolver, "argumentResolver must not be null");
        Validate.notNull(returnValueConverter, "returnValueConverter must not be null");
        Validate.notNull(exceptionConverter, "exceptionConverter must not be null");
        Validate.notNull(eventBusService, "eventBusService must not be null");
        Validate.notNull(reactiveAdapterRegistry, "reactiveAdapterRegistry must not be null");
        Validate.notNull(vertx, "vertx must not be null");
        Validate.notNull(securityContext, "securityContext must not be null");
        Validate.notNull(openTelemetry, "openTelemetry must not be null");
        Validate.notNull(traceLogFilter, "traceLogFilter must not be null");

        this.serviceDescriptor = serviceDescriptor;
        this.argumentResolver = argumentResolver;
        this.returnValueConverter = returnValueConverter;
        this.exceptionConverter = exceptionConverter;
        this.eventBusService = eventBusService;
        this.securityContext = securityContext;
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
        this.vertx = vertx;
        this.traceLogFilter = traceLogFilter;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();

        this.methodMap = buildMethodMap(serviceDescriptor, instanceProvider);
        this.scopeOptionalMethodIds = buildScopeOptionalMethodIds(serviceDescriptor);
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
            methodInvocationEventConsumer = listenAt(serviceDescriptor.serviceIdentifier().cri());

            Future<Void> ret = methodInvocationEventConsumer.completion();
            // a scoped service with ScopeOptional methods also joins the shared unscoped
            // address, where any instance may answer the methods that opted in
            if(serviceDescriptor.serviceIdentifier().scope() != null && !scopeOptionalMethodIds.isEmpty()){
                unscopedInvocationEventConsumer = listenAt(serviceDescriptor.serviceIdentifier().unscopedCri());
                ret = Future.all(ret, unscopedInvocationEventConsumer.completion()).mapEmpty();
            }
            return ret;
        }else{
            return Future.failedFuture(new IllegalStateException("Service already started"));
        }
    }

    private EventConsumer listenAt(CRI cri){
        EventConsumer consumer = eventBusService.listen(cri);
        consumer.handler(event -> vertx.executeBlocking(() -> {
                    processEvent(event);
                    return null;
                }))
                .exceptionHandler(throwable -> log.error("Event listener error", throwable))
                // Vert.x invokes the end handler on every unregistration, including the one stop() performs.
                // stop() clears active before unregistering, so a successful CAS here means something other
                // than stop() unregistered the consumer and this supervisor is no longer fully serving
                // invocations - losing either of its addresses makes it inactive
                .endHandler(_ -> {
                    if(active.compareAndSet(true, false)){
                        log.warn("Event listener for {} was unregistered without a call to stop(), supervisor is now inactive",
                                 cri);
                    }
                });
        return consumer;
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

            Future<Void> ret = methodInvocationEventConsumer.unregister();
            if(unscopedInvocationEventConsumer != null){
                ret = Future.all(ret, unscopedInvocationEventConsumer.unregister()).mapEmpty();
            }
            return ret;
        }else{
            return Future.failedFuture(new IllegalStateException("Service already stopped"));
        }
    }

    private Map<String, HandlerMethod> buildMethodMap(ServiceDescriptor serviceDescriptor,
                                                      FunctionInstanceProvider instanceProvider) {
        final HashMap<String, HandlerMethod> ret = new HashMap<>();

        for(FunctionDescriptor functionDescriptor : serviceDescriptor.functions()){
            Object instance = instanceProvider.provideInstance(functionDescriptor);
            Method specificMethod = AopUtils.selectInvocableMethod(functionDescriptor.invocationMethod(), instance.getClass());

            // add a / since uri paths contain this
            String methodName = "/" + specificMethod.getName();

            if(ret.containsKey(methodName)){
                throw new IllegalArgumentException("Multiple FunctionDescriptors provided with the name " + specificMethod.getName());
            }else{
                HandlerMethod handlerMethod = new HandlerMethod(instance, specificMethod);
                ret.put(methodName,  handlerMethod);
            }
        }
        return ret;
    }

    /**
     * The method ids the shared unscoped address may invoke: those the published interface
     * annotated {@link ScopeOptional}, keyed like {@link #methodMap}.
     */
    private Set<String> buildScopeOptionalMethodIds(ServiceDescriptor serviceDescriptor) {
        final Set<String> ret = new HashSet<>();
        for(FunctionDescriptor functionDescriptor : serviceDescriptor.functions()){
            if(AnnotationUtils.findAnnotation(functionDescriptor.invocationMethod(), ScopeOptional.class) != null){
                ret.add("/" + functionDescriptor.invocationMethod().getName());
            }
        }
        return ret;
    }

    private void convertAndSend(Metadata incomingMetadata, HandlerMethod handlerMethod, Object result) {
        convertAndSend(incomingMetadata, handlerMethod, result, null, true);
    }

    private void convertAndSend(Metadata incomingMetadata, HandlerMethod handlerMethod, Object result, String originCri) {
        convertAndSend(incomingMetadata, handlerMethod, result, originCri, false);
    }

    private void convertAndSend(Metadata incomingMetadata, HandlerMethod handlerMethod, Object result, String originCri, boolean terminal) {
        try {
            Event<byte[]> resultEvent = returnValueConverter.convert(incomingMetadata,
                                                                     handlerMethod.getReturnType(),
                                                                     result);
            // Set the origin CRI on the reply so a streaming client can route a cancel back to this service.
            if (originCri != null) {
                resultEvent.metadata().put(EventConstants.ORIGIN_CRI_HEADER, originCri);
            }
            // A single-value reply is the end of its request, so it carries the completion marker itself.
            // This lets any hop holding per-request state (client, gateway) release it on this one event
            // without knowing the invoked method's shape. Stream values stay unmarked; their completion
            // is the separate event sendCompletionEvent sends.
            if (terminal) {
                resultEvent.metadata().put(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_COMPLETE);
            }
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

        activeStreamingResults.computeIfPresent(correlationId, (_, streamSubscriber) -> {
            streamSubscriber.processControlEvent(incomingEvent);
            return streamSubscriber;
        });
    }

    private void processEvent(Event<byte[]> incomingEvent){
        boolean isControl = incomingEvent.metadata().contains(EventConstants.CONTROL_HEADER);

        if(log.isTraceEnabled() && !traceLogFilter.isExcluded(incomingEvent)){
            log.trace("Service {} requested for {}", isControl ? "Control" : "Invocation", incomingEvent.cri());
        }

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

                // A scoped service answers on the shared unscoped address only for methods that
                // declared themselves instance-independent: an instance-affine method invoked
                // without a scope would execute on whichever instance received it, so it fails
                // loud instead
                if(serviceDescriptor.serviceIdentifier().scope() != null
                        && !incomingEvent.cri().hasScope()
                        && !scopeOptionalMethodIds.contains(incomingEvent.cri().path())){
                    throw new RpcInvocationException("Method " + incomingEvent.cri().path()
                            + " of " + serviceDescriptor.serviceIdentifier().qualifiedName()
                            + " requires a scoped invocation naming the service instance");
                }

                if (!returnValueConverter.supports(incomingEvent.metadata(),
                                                   handlerMethod.getReturnType())) {
                    throw new IllegalStateException("No compatible ReturnValueConverter found");
                }

                // Inject the Participant into the Vert.x context so service methods can access it via context.getLocal()
                Participant participant = incomingEvent.sender();
                if (participant != null) {
                    Context context = Vertx.currentContext();
                    if (context != null) {
                        securityContext.setParticipant(context, participant);
                    }
                }

                Span span = startInvocationSpan(incomingEvent);
                // Making the span current is what nests everything the service method touches —
                // Elasticsearch calls, outbound HTTP, @WithSpan methods — underneath this invocation.
                try (Scope ignored = span.makeCurrent()) {

                    Object[] arguments = argumentResolver.resolveArguments(incomingEvent, handlerMethod);

                    if(log.isTraceEnabled() && !traceLogFilter.isExcluded(incomingEvent)){
                        log.trace(handlerMethod.formatInvokeMessage("Invoking ", arguments));
                    }

                    // separate try catch since we do not want to log invocation errors
                    Object result = null;
                    boolean error = false;
                    try {
                        // Invoke the method and then handle the result
                        result = handlerMethod.invoke(arguments);
                    } catch (Exception e) {
                        error = true;
                        failSpan(span, e);
                        handleException(incomingEvent.metadata(), e);
                    }

                    if (!error) {
                        // A reactive result is still in flight here, so the span ends with the result
                        // rather than with this method.
                        processMethodInvocationResult(incomingEvent, handlerMethod, result, span);
                    }

                } catch (Exception e) {
                    failSpan(span, e);
                    throw e;
                }

        } else {
            throw new IllegalStateException("No compatible ArgumentResolver found");
        }
    }

    private void processMethodInvocationResult(Event<byte[]> incomingEvent, HandlerMethod handlerMethod, Object result, Span span){

        Metadata incomingMetadata = incomingEvent.metadata();

        // Check if result is reactive if so we only complete once result is complete
        ReactiveAdapter reactiveAdapter = reactiveAdapterRegistry.getAdapter(null, result);
        if(reactiveAdapter == null){

            convertAndSend(incomingMetadata, handlerMethod, result);
            span.end();

        }else{

            if(!reactiveAdapter.isMultiValue()){

                Publisher<?> publisher = reactiveAdapter.toPublisher(result);
                publisher.subscribe(new SingleValueSubscriber(incomingMetadata, handlerMethod, incomingEvent, span));

            }else{

                // All long-running results require a CORRELATION_ID_HEADER to be able to coordinate with the requester
                if(!incomingEvent.metadata().contains(EventConstants.CORRELATION_ID_HEADER)){
                    throw new IllegalArgumentException("Streaming results require a CORRELATION_ID_HEADER to be set");
                }

                String correlationId = incomingEvent.metadata().get(EventConstants.CORRELATION_ID_HEADER);

                activeStreamingResults.computeIfAbsent(correlationId, _ -> {
                    Flux<?> flux = Flux.from(reactiveAdapter.toPublisher(result));

                    CRI replyCRI = CRI.create(incomingEvent.metadata().get(EventConstants.REPLY_TO_HEADER));
                    Flux<ListenerStatus> replyListenerStatus = eventBusService.monitorListenerStatus(replyCRI);

                    StreamSubscriber streamSubscriber = new StreamSubscriber(incomingMetadata, handlerMethod, replyListenerStatus, incomingEvent.cri().raw(), span);
                    flux.subscribe(streamSubscriber);
                    return streamSubscriber;
                });
            }
        }
    }

    /**
     * Starts the span covering one service method invocation, continuing the caller's trace when the
     * incoming event carries a trace context.
     * @param incomingEvent the invocation request, whose metadata may carry the caller's trace context
     * @return a started {@link Span} the caller is responsible for ending
     */
    private Span startInvocationSpan(Event<byte[]> incomingEvent){
        io.opentelemetry.context.Context parent = propagator.extract(io.opentelemetry.context.Context.current(),
                                                                     incomingEvent.metadata(),
                                                                     TelemetryUtil.METADATA_GETTER);
        // The path is the method id, carrying the leading / the method map is keyed by
        String methodName = incomingEvent.cri().path().substring(1);
        String serviceName = serviceDescriptor.serviceIdentifier().qualifiedName();
        return tracer.spanBuilder(serviceDescriptor.serviceIdentifier().name() + "/" + methodName)
                     .setParent(parent)
                     .setSpanKind(SpanKind.SERVER)
                     .setAttribute(TelemetryUtil.RPC_SYSTEM, TelemetryUtil.SYSTEM_VALUE)
                     .setAttribute(TelemetryUtil.RPC_SERVICE, serviceName)
                     .setAttribute(TelemetryUtil.RPC_METHOD, methodName)
                     .startSpan();
    }

    private void failSpan(Span span, Throwable throwable){
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
        span.end();
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
                if (replyTo.startsWith(EventConstants.REPLY_DESTINATION_SCHEME + ":")) {
                    ret = true;
                } else {
                    log.warn("Reply-to header must be a valid reply destination");
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
        private final Span span;
        private boolean valueReceived = false;

        public SingleValueSubscriber(Metadata incomingMetadata, HandlerMethod handlerMethod, Event<byte[]> incomingEvent, Span span) {
            this.incomingMetadata = incomingMetadata;
            this.handlerMethod = handlerMethod;
            this.incomingEvent = incomingEvent;
            this.span = span;
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
            failSpan(span, t);
        }

        @Override
        public void onComplete() {
            if(!valueReceived){
                convertAndSend(incomingMetadata, handlerMethod, null);
            }
            span.end();
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
        protected void hookOnError(@NonNull Throwable throwable) {
            // This condition should not occur under normal operation
            log.error("Reply Listener Monitor threw an exception. Terminating streaming result.", throwable);
            streamSubscription.cancel();
        }

        @Override
        protected void hookOnNext(@NonNull ListenerStatus status) {
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
        private final String originCri;
        private final Span span;
        private ReplyListenerStatusSubscriber replyListenerStatusSubscriber;

        public StreamSubscriber(Metadata incomingMetadata,
                                HandlerMethod handlerMethod,
                                Flux<ListenerStatus> replyListenerStatus,
                                String originCri,
                                Span span) {
            this.incomingMetadata = incomingMetadata;
            this.handlerMethod = handlerMethod;
            this.replyListenerStatus = replyListenerStatus;
            this.originCri = originCri;
            this.span = span;
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
        protected void hookFinally(@NonNull SignalType type) {
            log.trace("Stream Cleanup Now");

            // Covers every terminal signal the stream can end on, cancellation included
            span.end();

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
        protected void hookOnError(@NonNull Throwable throwable) {
            if(log.isTraceEnabled()){
                log.trace("Stream Error",throwable);
            }
            handleException(incomingMetadata, throwable);
            // hookFinally ends the span, this only marks why it ended
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR);
        }

        @Override
        protected void hookOnNext(@NonNull Object value) {
            if(log.isTraceEnabled() && !traceLogFilter.isExcluded(originCri)){
                log.trace("Next stream value {}", value);
            }
            convertAndSend(incomingMetadata, handlerMethod, value, originCri);
        }

        @Override
        protected void hookOnSubscribe(@NonNull Subscription subscription) {

            replyListenerStatusSubscriber = new ReplyListenerStatusSubscriber(this);
            replyListenerStatus.subscribe(replyListenerStatusSubscriber);

            super.hookOnSubscribe(subscription);
        }

    }

}
