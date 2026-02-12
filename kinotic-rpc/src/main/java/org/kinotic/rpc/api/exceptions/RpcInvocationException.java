

package org.kinotic.rpc.api.exceptions;

/**
 * This exception is thrown When the original error message cannot be rethrown during a rpc service invocation
 *
 * Created by navid on 11/7/19
 */
public class RpcInvocationException extends ContinuumException {

    private String originalClassName;

    private StackTraceElement[] originalStackTrace;

    public RpcInvocationException(String message) {
        super(message);
    }

    public String getOriginalClassName() {
        return originalClassName;
    }

    public RpcInvocationException setOriginalClassName(String originalClassName) {
        this.originalClassName = originalClassName;
        return this;
    }

    public StackTraceElement[] getOriginalStackTrace() {
        return originalStackTrace;
    }

    public RpcInvocationException setOriginalStackTrace(StackTraceElement[] originalStackTrace) {
        this.originalStackTrace = originalStackTrace;
        return this;
    }
}
