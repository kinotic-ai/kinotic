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

package org.kinotic.continuum.core.api.service;

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
