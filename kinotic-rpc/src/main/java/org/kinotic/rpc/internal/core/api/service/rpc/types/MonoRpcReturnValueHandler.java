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

package org.kinotic.rpc.internal.core.api.service.rpc.types;

import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.rpc.internal.core.api.service.ExceptionConverter;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcRequest;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcResponseConverter;
import org.kinotic.rpc.internal.core.api.service.rpc.RpcReturnValueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

/**
 * {@link RpcReturnValueHandler} that handles {@link Mono}'s
 *
 * Created by navid on 10/29/19
 */
public class MonoRpcReturnValueHandler implements RpcReturnValueHandler {

    private static final Logger log = LoggerFactory.getLogger(MonoRpcReturnValueHandler.class);

    private final MethodParameter methodParameter;
    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;
    private MonoSink<Object> monoSink;
    private String cancelMessage = null;

    public MonoRpcReturnValueHandler(MethodParameter methodParameter,
                                     RpcResponseConverter rpcResponseConverter,
                                     ExceptionConverter exceptionConverter) {

        Assert.notNull(methodParameter, "methodParameter must not be null");
        Assert.notNull(rpcResponseConverter, "responseConverter must not be null");
        Assert.notNull(exceptionConverter, "exceptionConverter must not be null");

        this.methodParameter = methodParameter;
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public boolean processResponse(Event<byte[]> incomingEvent) {
        // sanity check
        // API contract is supposed to guarantee that processResponse will never be called until RpcRequest.send() is called..
        if(monoSink == null){
            log.error("For some reason processResponse was called before the MonoSink was set. This should never happen!!");
        }else {
            try {
                // Error data is returned differently
                if(incomingEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                    monoSink.error(exceptionConverter.convert(incomingEvent));
                }else{
                    monoSink.success(rpcResponseConverter.convert(incomingEvent, methodParameter));
                }
            } catch (Exception e) {
                log.error("Error converting the incoming message to expected java type", e);
                monoSink.error(e);
            }
        }
        return true;
    }

    @Override
    public boolean isMultiValue() {
        return false;
    }

    @Override
    public synchronized Object getReturnValue(RpcRequest rpcRequest) {
        //noinspection ReactiveStreamsUnusedPublisher
        return Mono.create(objectMonoSink -> {
            monoSink = objectMonoSink;
            // in case this was canceled before the Mono was subscribed to
            if(cancelMessage == null){
                rpcRequest.send();
            }else{
                monoSink.error(new IllegalStateException(cancelMessage));
            }
        });
    }

    @Override
    public void processError(Throwable throwable) {
        monoSink.error(throwable);
    }

    @Override
    public synchronized void cancel(String message) {
        // cancel can be called before getReturnValue()
        if(monoSink != null){
            monoSink.error(new IllegalStateException(message));
        }else{
            cancelMessage = message;
        }
    }

}
