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

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/24/23.
 */
public class CompletableFutureValueHandler implements RpcReturnValueHandler {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureValueHandler.class);

    private final MethodParameter methodParameter;
    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;
    private final CompletableFuture<Object> returnValue;

    public CompletableFutureValueHandler(MethodParameter methodParameter,
                                         RpcResponseConverter rpcResponseConverter,
                                         ExceptionConverter exceptionConverter) {

        Assert.notNull(methodParameter, "methodParameter must not be null");
        Assert.notNull(rpcResponseConverter, "responseConverter must not be null");
        Assert.notNull(exceptionConverter, "exceptionConverter must not be null");

        this.methodParameter = methodParameter;
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
        this.returnValue = new CompletableFuture<>();
    }
    
    @Override
    public boolean processResponse(Event<byte[]> incomingEvent) {
        try{
            // Error data is returned differently
            if(incomingEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                returnValue.completeExceptionally(exceptionConverter.convert(incomingEvent));
            }else{
                returnValue.complete(rpcResponseConverter.convert(incomingEvent, methodParameter));
            }
        }catch (Exception e){
            log.error("Error converting the incoming message to expected java type", e);
            returnValue.completeExceptionally(e);
        }
        return true;
    }

    @Override
    public Object getReturnValue(RpcRequest rpcRequest) {
        rpcRequest.send();
        return returnValue;
    }

    @Override
    public boolean isMultiValue() {
        return false;
    }

    @Override
    public void processError(Throwable throwable) {
        returnValue.completeExceptionally(throwable);
    }

    @Override
    public void cancel(String message) {
        returnValue.completeExceptionally(new IllegalStateException(message));
    }

}
