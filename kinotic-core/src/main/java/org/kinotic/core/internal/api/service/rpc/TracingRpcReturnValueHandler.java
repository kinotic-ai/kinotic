package org.kinotic.core.internal.api.service.rpc;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.kinotic.core.api.event.Event;

/**
 * Keeps the span of an outbound service invocation open until the invocation settles, delegating
 * every decision about the response itself to the handler it wraps.
 *
 * Created by Claude on 2026-08-15.
 */
public class TracingRpcReturnValueHandler implements RpcReturnValueHandler {

    private final RpcReturnValueHandler delegate;
    private final Span span;

    public TracingRpcReturnValueHandler(RpcReturnValueHandler delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
    }

    @Override
    public Object getReturnValue(RpcRequest rpcRequest) {
        return delegate.getReturnValue(rpcRequest);
    }

    @Override
    public boolean isMultiValue() {
        return delegate.isMultiValue();
    }

    @Override
    public boolean processResponse(Event<byte[]> incomingEvent) {
        boolean ret = delegate.processResponse(incomingEvent);
        // A streaming invocation keeps producing responses, so only the last one ends the span
        if (ret) {
            span.end();
        }
        return ret;
    }

    @Override
    public void processError(Throwable throwable) {
        delegate.processError(throwable);
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
        span.end();
    }

    @Override
    public void cancel(String message) {
        delegate.cancel(message);
        span.end();
    }

}
