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

package org.kinotic.continuum.internal.core.api.service.invoker;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Encapsulates information about a handler method consisting of a
 * {@linkplain #getMethod() method} and a {@linkplain #getBean() bean}.
 * Provides convenient access to method parameters, the method return value,
 * method annotations, etc.
 * This is an adaptation of the spring-web HandlerMethod
 * Created by Navid Mitchell on 2019-03-25.
 */
public class HandlerMethod {

    private static final Logger log = LoggerFactory.getLogger(HandlerMethod.class);

    /**
     * -- GETTER --
     *  Return the bean for this handler method.
     */
    @Getter
    private final Object bean;

    /**
     * -- GETTER --
     *  This method returns the type of the handler for this handler method.
     *  <p>Note that if the bean type is a CGLIB-generated class, the original
     *  user-defined class is returned.
     */
    @Getter
    private final Class<?> beanType;
    private final Method bridgedMethod;
    /**
     * -- GETTER --
     *  Return the method for this handler method.
     */
    @Getter
    private final Method method;
    private final MethodParameter[] parameters;

    /**
     * Create an instance from a bean instance and a method.
     */
    public HandlerMethod(Object bean, Method method) {
        Validate.notNull(bean, "Bean is required");
        Validate.notNull(method, "Method is required");
        this.bean = bean;
        this.beanType = ClassUtils.getUserClass(bean);
        this.method = method;
        this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        this.parameters = initMethodParameters();
    }

    /**
     * Return the method parameters for this handler method.
     */
    public MethodParameter[] getMethodParameters() {
        return this.parameters;
    }

    /**
     * Return the HandlerMethod return type.
     */
    public MethodParameter getReturnType() {
        return new MethodParameter(this.bridgedMethod, -1);
    }

    /**
     * Invokes the handler with the given arguments provided
     * The method invocation will happen in a background thread
     *
     * @param args to pass to the method to be invokeds
     *
     * @return the result of the invocation
     */
    public Object invoke(Object... args) throws Exception{
        Validate.isTrue(args.length == parameters.length, "Wrong number of parameters provided, Expected: "+parameters.length+" Got: "+args.length);
        return doInvoke(args);
    }

    /**
     * Assert that the target bean class is an instance of the class where the given
     * method is declared. In some cases the actual controller instance at request
     * processing time may be a JDK dynamic proxy (lazy initialization, prototype
     * beans, and others). {@code @Controller}'s that require proxying should prefer
     * class-based proxy mechanisms.
     */
    protected void assertTargetBean(Method method, Object targetBean, Object[] args) {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        Class<?> targetBeanClass = targetBean.getClass();
        if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
            String text = "The mapped handler method class '" + methodDeclaringClass.getName() +
                    "' is not an instance of the actual service bean class '" +
                    targetBeanClass.getName() + "'. If the service requires proxying " +
                    "(e.g. due to @Transactional), please use class-based proxying.";
            throw new IllegalStateException(formatInvokeMessage(text, args));
        }
    }

    /**
     * Invoke the handler method with the given argument values.
     */
    protected Object doInvoke(Object... args) throws Exception {
        Method method = getBridgedMethod();
        ReflectionUtils.makeAccessible(method);
        try {
            if(log.isTraceEnabled()){
                log.trace(formatInvokeMessage("Invoking ", args));
            }

            return method.invoke(getBean(), args);
        }
        catch (IllegalArgumentException ex) {
            assertTargetBean(getBridgedMethod(), getBean(), args);
            String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
            throw new IllegalStateException(formatInvokeMessage(text, args), ex);
        }
        catch (InvocationTargetException ex) {
            // Unwrap for HandlerExceptionResolvers ...
            Throwable targetException = ex.getTargetException();
            switch (targetException) {
                case RuntimeException runtimeException -> throw runtimeException;
                case Error error -> throw error;
                case Exception exception -> throw exception;
                case null, default -> throw new IllegalStateException(formatInvokeMessage("Invocation failure", args),
                                                                      targetException);
            }
        }
    }

    protected String formatInvokeMessage(String text, Object[] args) {
        String formattedArgs = IntStream.range(0, args.length)
                .mapToObj(i -> (args[i] != null ?
                        "[" + i + "] [type=" + args[i].getClass().getName() + "] [value=" + args[i] + "]" :
                        "[" + i + "] [null]"))
                .collect(Collectors.joining(",\n", " ", " "));
        return text + "\n" +
                "Service [" + getBeanType().getName() + "]\n" +
                "Method [" + getBridgedMethod().toGenericString() + "] " +
                "with argument values:\n" + formattedArgs;
    }

    /**
     * If the bean method is a bridge method, this method returns the bridged
     * (user-defined) method. Otherwise it returns the same method as {@link #getMethod()}.
     */
    protected Method getBridgedMethod() {
        return this.bridgedMethod;
    }

    private MethodParameter[] initMethodParameters() {
        int count = this.bridgedMethod.getParameterCount();
        MethodParameter[] result = new MethodParameter[count];
        for (int i = 0; i < count; i++) {
            MethodParameter parameter = new MethodParameter(this.bridgedMethod, i)
                                                .withContainingClass(this.beanType);
            result[i] = parameter;
        }
        return result;
    }

    @Override
    public String toString() {
        return this.method.toGenericString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HandlerMethod otherMethod)) {
            return false;
        }
        return (this.bean.equals(otherMethod.bean) && this.method.equals(otherMethod.method));
    }

    @Override
    public int hashCode() {
        return (this.bean.hashCode() * 31 + this.method.hashCode());
    }

}
