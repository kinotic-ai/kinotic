

package org.kinotic.rpc.api.service;

/**
 * Wraps any exception that occurs during service invocation so that all the error information can be transmitted as a json object
 * Created by 🤓 on 6/12/21.
 */
public class ServiceExceptionWrapper {

    private String exceptionName;

    private String exceptionClass;

    private String errorMessage;

    private StackTraceElement[] stackTrace;

    public ServiceExceptionWrapper() {
    }

    public ServiceExceptionWrapper(String exceptionName,
                                   String exceptionClass,
                                   String errorMessage) {
        this.exceptionName = exceptionName;
        this.exceptionClass = exceptionClass;
        this.errorMessage = errorMessage;
    }

    public String getExceptionName() {
        return exceptionName;
    }

    public ServiceExceptionWrapper setExceptionName(String exceptionName) {
        this.exceptionName = exceptionName;
        return this;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public ServiceExceptionWrapper setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ServiceExceptionWrapper setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    public ServiceExceptionWrapper setStackTrace(StackTraceElement[] stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }
}
