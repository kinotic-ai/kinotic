

package org.kinotic.rpc.api.service;

import org.kinotic.rpc.api.ServiceRegistry;

import java.util.Map;

/**
 * The {@link ServiceFunctionInstanceProvider} provides object instances for {@link ServiceFunction}'s
 * This allows the {@link ServiceFunction#invocationMethod()} to be invoked with the proper object instance
 *
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
public interface ServiceFunctionInstanceProvider {

    /**
     * This method will be called by the {@link ServiceRegistry}
     * for all {@link ServiceFunction}'s that are part of any registered {@link ServiceDescriptor}
     * The provider will be called when the {@link ServiceDescriptor} is registered
     *
     * @param serviceFunction that the Object instance should be provided for
     * @return the correct Object instance for the {@link ServiceFunction}
     */
    Object provideInstance(ServiceFunction serviceFunction);


    static ServiceFunctionInstanceProvider create(Object instance){
        return serviceFunction -> instance;
    }

    static ServiceFunctionInstanceProvider create(Map<ServiceFunction, Object> functionMap){
        return functionMap::get;
    }

}
