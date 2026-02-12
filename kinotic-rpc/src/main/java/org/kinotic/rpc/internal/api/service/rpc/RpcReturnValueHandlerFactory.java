

package org.kinotic.rpc.internal.api.service.rpc;

import java.lang.reflect.Method;

/**
 * Creates a {@link RpcReturnValueHandler} for the given {@link java.lang.reflect.Method} and arguments
 *
 *
 * Created by navid on 2019-04-24.
 */
public interface RpcReturnValueHandlerFactory {

    /**
     * Determine if this {@link RpcReturnValueHandlerFactory} supports the given {@link Method}
     * @param method to check if supported by this {@link RpcReturnValueHandlerFactory}
     * @return true if supported false if not
     */
    boolean supports(Method method);

    /**
     * Provides the {@link RpcReturnValueHandler} for the given parameters
     * @param method the method that is being invoked by the proxy
     * @param args that were provided to the method being invoked
     * @return the new {@link RpcReturnValueHandler}
     */
    RpcReturnValueHandler createReturnValueHandler(Method method, Object... args);

}
