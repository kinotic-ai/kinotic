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

package org.kinotic.continuum.internal.core.api.service.rpc.types;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.internal.core.api.service.ExceptionConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcRequest;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcResponseConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcReturnValueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;

/**
 * Return value handler that provides a {@link Future}
 *
 * Created by navid on 2019-04-25.
 */
public class VertxFutureRpcReturnValueHandler implements RpcReturnValueHandler {

    private static final Logger log = LoggerFactory.getLogger(VertxFutureRpcReturnValueHandler.class);

    private final MethodParameter methodParameter;
    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;
    private final Promise<Object> promise;

    public VertxFutureRpcReturnValueHandler(MethodParameter methodParameter,
                                            RpcResponseConverter rpcResponseConverter,
                                            ExceptionConverter exceptionConverter) {

        Assert.notNull(methodParameter, "methodParameter must not be null");
        Assert.notNull(rpcResponseConverter, "responseConverter must not be null");
        Assert.notNull(exceptionConverter, "exceptionConverter must not be null");

        this.methodParameter = methodParameter;
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
        this.promise = Promise.promise();
    }

    @Override
    public boolean processResponse(Event<byte[]> incomingEvent) {
        try{
            // Error data is returned differently
            if(incomingEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                promise.fail(exceptionConverter.convert(incomingEvent));
            }else{
                promise.complete(rpcResponseConverter.convert(incomingEvent, methodParameter));
            }
        }catch (Exception e){
            log.error("Error converting the incoming message to expected java type", e);
            promise.fail(e);
        }
        return true;
    }

    @Override
    public boolean isMultiValue() {
        return false;
    }

    @Override
    public Object getReturnValue(RpcRequest rpcRequest) {
        rpcRequest.send();
        return promise.future();
    }

    @Override
    public void processError(Throwable throwable) {
        promise.fail(throwable);
    }

    @Override
    public void cancel(String message) {
        promise.fail(message);
    }

}
