/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.core.api.service.rpc;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kinotic.continuum.api.exceptions.RpcMissingServiceException;
import org.kinotic.continuum.core.api.RpcServiceProxy;
import org.kinotic.continuum.core.api.RpcServiceProxyHandle;
import org.kinotic.continuum.core.api.event.*;
import org.kinotic.continuum.core.api.service.ServiceIdentifier;
import org.kinotic.continuum.internal.utils.ContinuumUtil;
import org.kinotic.continuum.internal.utils.MetaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of the {@link RpcServiceProxyHandle}
 * Will send all service requests on the Vertx {@link EventBus}
 *
 * Created by navid on 2019-04-18.
 */
public class DefaultRpcServiceProxyHandle<T> implements RpcServiceProxyHandle<T>, InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultRpcServiceProxyHandle.class);

    private final ServiceIdentifier serviceIdentifier;
    private final String nodeName;
    private final Class<T> serviceClass;
    private final CRI handlerCRI;
    private final RpcArgumentConverter rpcArgumentConverter;
    private final RpcReturnValueHandlerFactory rpcReturnValueHandlerFactory;
    private final EventBusService eventBusService;

    private final Map<Method, Integer> methodsWithScopeAnnotation = new HashMap<>();
    private final Disposable replyEventListenerDisposable;
    private final T serviceProxy;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private final ConcurrentHashMap<String, RpcReturnValueHandler> responseMap = new ConcurrentHashMap<>();

    public DefaultRpcServiceProxyHandle(ServiceIdentifier serviceIdentifier,
                                        String nodeName,
                                        Class<T> serviceClass,
                                        RpcArgumentConverter rpcArgumentConverter,
                                        RpcReturnValueHandlerFactory rpcReturnValueHandlerFactory,
                                        EventBusService eventBusService,
                                        ClassLoader classLoader) {

        Validate.notNull(serviceIdentifier, "serviceIdentifier must not be null");
        Validate.notBlank(nodeName, "nodeName must not be blank");
        Validate.notNull(serviceClass, "serviceClass must not be null");
        Validate.notNull(rpcArgumentConverter, "argumentConverter must not be null");
        Validate.notNull(rpcReturnValueHandlerFactory, "returnValueHandlerFactory must not be null");
        Validate.notNull(eventBusService, "eventBusService must not be null");
        Validate.notNull(classLoader, "classLoader must not be null");

        this.serviceIdentifier = serviceIdentifier;
        this.nodeName = nodeName;
        String encodedNodeName = ContinuumUtil.safeEncodeURI(nodeName);
        this.serviceClass = serviceClass;
        this.rpcArgumentConverter = rpcArgumentConverter;
        this.rpcReturnValueHandlerFactory = rpcReturnValueHandlerFactory;
        this.eventBusService = eventBusService;

        this.handlerCRI = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, encodedNodeName + ":" + UUID.randomUUID(), ContinuumUtil.safeEncodeURI(serviceClass.getName())+"RpcProxyResponseHandler");

        // Verify that a proxy can be built supporting all methods of the provided serviceClass
        ReflectionUtils.doWithMethods(serviceClass, method -> {

            if(!rpcReturnValueHandlerFactory.supports(method)){
                throw new IllegalArgumentException("The method: "+ method +" does not have a supported RpcReturnValueHandlerFactory");
            }

            // find any parameters annotated with @Scope
            // we populate this map upfront to speed up invocations later
            Integer parameterIndexWithScopeAnnotation = MetaUtil.findParameterIndexWithScopeAnnotation(method);
            if(parameterIndexWithScopeAnnotation != null){
                methodsWithScopeAnnotation.put(method, parameterIndexWithScopeAnnotation);
            }

        }, ReflectionUtils.USER_DECLARED_METHODS);

        serviceProxy = serviceClass.cast(Proxy.newProxyInstance(classLoader, new Class[] { serviceClass, RpcServiceProxy.class}, this));

        /*
          Response handler logic to correlate response from remote service invocation's
         */
        Flux<Event<byte[]>> eventFlux = eventBusService.listen(this.handlerCRI.raw());
        replyEventListenerDisposable =
                eventFlux.subscribe(event -> {

                    String correlationId = event.metadata().get(EventConstants.CORRELATION_ID_HEADER);
                    if(correlationId != null){
                        if(responseMap.containsKey(correlationId)){
                            try {
                                // provide message to handler for processing
                                RpcReturnValueHandler handler = responseMap.get(correlationId);
                                if(handler.processResponse(event)){
                                    responseMap.remove(correlationId);
                                }
                            } catch (Exception e) {
                                log.error("URGENT: Unhandled exception in RpcReturnValueHandler.processResponse, Proxy Will be Released!!", e);
                                release();
                            }
                        }else{
                            log.error("Received Message for correlationId: {} but no response handler is set", correlationId);
                        }

                    }else{
                        log.error("Received Message with no " + EventConstants.CORRELATION_ID_HEADER +" header");
                    }
                },
                // received error
                throwable -> log.error("Reply Event listener error", throwable),
                // listener complete
                () -> log.error("Should not happen! Reply Event listener stopped for some reason!!"));
    }

    @Override
    public T getService() {
        return serviceProxy;
    }

    @Override
    public void release() {
        if(released.compareAndSet(false,true)){
            replyEventListenerDisposable.dispose();

            responseMap.forEach((s, returnValueHandler) -> returnValueHandler.cancel(serviceClass.getSimpleName() + " released. No further responses will be processed"));
            responseMap.clear();
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object ret;
        if(!released.get()){

            if(log.isTraceEnabled()){
                log.trace("Proxy for {} Method Invoked {}", serviceClass.getSimpleName(), method.toString());
            }

            if(!shouldInvokeLocally(method)) {

                // Get all data for remote invocation. If anything fails in this step the error automatically props up
                // This way no ReturnValueHandler is created until message is ready to get dispatched to remote end


                // if there is a scope parameter remove it from args to be sent, and store it as part of the CRI
                Integer scopeParameter = methodsWithScopeAnnotation.get(method);
                String scope = (scopeParameter != null ? args[scopeParameter].toString() : null); // effectively final.. lol
                if(scopeParameter != null){
                    args = ArrayUtils.remove(args, scopeParameter);
                }

                // convert arguments to be sent
                byte[] argumentData = rpcArgumentConverter.convert(method, args);
                String correlationId = UUID.randomUUID().toString();

                // Now create response handler and store, so we can propagate response in replyMessageConsumer
                RpcReturnValueHandler handler = rpcReturnValueHandlerFactory.createReturnValueHandler(method, args);
                responseMap.put(correlationId, handler);

                // Create Event to be sent to remote end to cause service invocation
                Metadata metadata = Metadata.create();
                // For right now we just supply a fairly generic participant
                // It is probably not really useful in production
                metadata.put(EventConstants.SENDER_HEADER, "{\"tenantId\":\"continuum\",\"id\":\""+nodeName+"\",\"metadata\":{\"type\":\"node\"},\"roles\":[\"NODE\"]}");
                metadata.put(EventConstants.REPLY_TO_HEADER, handlerCRI.raw());
                metadata.put(EventConstants.CORRELATION_ID_HEADER, correlationId);
                metadata.put(EventConstants.CONTENT_TYPE_HEADER, rpcArgumentConverter.producesContentType());

                // TODO: use version string to determine how specific the invocation has to be like npm semantics ^1.0.0 ect
                CRI requestCri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME,
                                            scope,
                                            serviceIdentifier.qualifiedName(),
                                            "/" + method.getName(),
                                            serviceIdentifier.version());

                Event<byte[]> rpcOutboundEvent = Event.create(requestCri,
                                                              metadata,
                                                              argumentData);

                ret = handler.getReturnValue(new RpcRequest() {
                    @Override
                    public void send() {
                        // Send data to remote end to trigger service invocation
                        eventBusService.sendWithAck(rpcOutboundEvent)
                                       .subscribe(v -> {},
                                                  throwable -> {
                                           // send failed, signal handler so failure can be relayed to the return value
                                           try{

                                               responseMap.remove(correlationId);

                                               // TODO: refactor into util, this is also done in the EndpointConnectionHandler
                                               if (throwable instanceof ReplyException replyException) {
                                                   if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                                                       throwable = new RpcMissingServiceException(throwable);
                                                   }
                                               }
                                               handler.processError(throwable);
                                           }catch (Exception e){
                                               log.error("URGENT: Unhandled exception in RpcReturnValueHandler.processError, Proxy Will be Released!!", e);
                                               release();
                                           }
                                       });
                    }

                    @Override
                    public void cancelRequest() {
                        if(handler.isMultiValue()) {
                            // Now publish message for remote control
                            Metadata metadata = Metadata.create();
                            metadata.put(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_CANCEL);
                            metadata.put(EventConstants.CORRELATION_ID_HEADER, correlationId);

                            // Send data to remote end for control request
                            eventBusService.sendWithAck(Event.create(requestCri,
                                                                     metadata,
                                                                     null))
                                           .doFinally(signalType -> responseMap.remove(correlationId))
                                           .subscribe();
                        } else {
                            throw new IllegalStateException("Cancel is not supported if RpcReturnValueHandler.isMultiValue returns false");
                        }
                    }
                });

            }else{
                // Method not defined on service interface pass call directly to this service handle. ex: toString()
                Class<?>[] paramTypes = new Class[args.length];
                for(int i = 0; i < args.length; i++){
                    paramTypes[i] = args[i].getClass();
                }
                Method serviceHandleMethod = ReflectionUtils.findMethod(this.getClass(), method.getName(), paramTypes);

                Assert.notNull(serviceHandleMethod, "Could not find appropriate method on proxy handle");

                ret = serviceHandleMethod.invoke(this, args);
            }

        }else{
           throw new IllegalStateException("RpcServiceProxyHandle has already been released. No service method can be called after release.");
        }
        return ret;
    }

    private boolean shouldInvokeLocally(Method method){
        boolean ret = false;

        String methodName = method.getName();
        if(methodName.equals("toString")){
            ret = true;
        }

        return ret;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("serviceIdentifier", serviceIdentifier)
                .append("handlerCRI", handlerCRI)
                .toString();
    }

}
