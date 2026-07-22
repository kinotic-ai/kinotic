

package org.kinotic.core.api.service;

import java.lang.reflect.Method;

/**
 * The node-local, invocable view of a service function, holding the live {@link Method} to invoke; the declarative,
 * serializable contract view is {@code FunctionDefinition} in kinotic-idl.
 * Created by Navíd Mitchell 🤪 on 7/18/21.
 */
public interface FunctionDescriptor {

    /**
     * The name of this {@link FunctionDescriptor}
     * @return string containing the name
     */
    String name();

    /**
     * The method that can be invoked for this {@link FunctionDescriptor}
     * @return the method to invoke
     */
    Method invocationMethod();

    static FunctionDescriptor create(String name, Method invocationMethod){
        return new DefaultFunctionDescriptor(name, invocationMethod);
    }

}
