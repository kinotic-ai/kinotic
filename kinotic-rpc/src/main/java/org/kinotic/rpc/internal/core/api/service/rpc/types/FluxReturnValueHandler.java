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
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 *
 * Created by Navid Mitchell on 5/30/20
 */
public class FluxReturnValueHandler implements RpcReturnValueHandler {
    private static final Logger log = LoggerFactory.getLogger(FluxReturnValueHandler.class);

    private final MethodParameter methodParameter;
    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;

    private FluxSink<Object> fluxSink;
    private String cancelMessage = null;

    public FluxReturnValueHandler(MethodParameter methodParameter,
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
        boolean finished = false;
        // sanity check
        // API contract is supposed to guarantee that processResponse will never be called until RpcRequest.send() is called..
        if(fluxSink == null){
            log.error("For some reason processResponse was called before the FluxSink was set. This should never happen!!");
        }else {
            if(!fluxSink.isCancelled()) {
                try {
                    if (incomingEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                        finished = true;
                        fluxSink.error(exceptionConverter.convert(incomingEvent));

                    } else if (incomingEvent.metadata().contains(EventConstants.CONTROL_HEADER)) {

                        String control = incomingEvent.metadata().get(EventConstants.CONTROL_HEADER);
                        if (control.equals(EventConstants.CONTROL_VALUE_COMPLETE)) {
                            finished = true;
                            fluxSink.complete();
                        } else {
                            finished = true;
                            log.warn("Unknown control header. Terminated flux with an error");
                            fluxSink.error(new IllegalStateException("Unknown control header"));
                        }

                    } else {
                        fluxSink.next(rpcResponseConverter.convert(incomingEvent, methodParameter));
                    }

                } catch (Exception e) {
                    log.error("Error converting the incoming message to expected java type", e);
                    finished = true;
                    fluxSink.error(e);
                }
            }else{
                finished = true;
            }
        }
        return finished;
    }

    @Override
    public boolean isMultiValue() {
        return true;
    }

    @Override
    public synchronized Object getReturnValue(RpcRequest rpcRequest) {
        return Flux.create(sink -> {
            fluxSink = sink;
            // in case this was canceled before the Flux was subscribed to
            if(cancelMessage == null){

                rpcRequest.send();

                fluxSink.onCancel(rpcRequest::cancelRequest);

            }else{
                fluxSink.error(new IllegalStateException(cancelMessage));
            }
        });
    }

    @Override
    public void processError(Throwable throwable) {
        fluxSink.error(throwable);
    }

    @Override
    public synchronized void cancel(String message) {
        // cancel can be called before getReturnValue()
        if(fluxSink != null){
            fluxSink.error(new IllegalStateException(message));
        }else{
            cancelMessage = message;
        }
    }

}
