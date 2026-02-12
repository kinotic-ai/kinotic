

package org.kinotic.rpc.api.service;

import java.lang.reflect.Method;

/**
 * Created by Navíd Mitchell 🤪 on 7/18/21.
 */
public interface ServiceFunction {

    /**
     * The name of this {@link ServiceFunction}
     * @return string containing the name
     */
    String name();

    /**
     * The method that can be invoked for this {@link ServiceFunction}
     * @return the method to invoke
     */
    Method invocationMethod();

    static ServiceFunction create(String name, Method invocationMethod){
        return new DefaultServiceFunction(name, invocationMethod);
    }

}
