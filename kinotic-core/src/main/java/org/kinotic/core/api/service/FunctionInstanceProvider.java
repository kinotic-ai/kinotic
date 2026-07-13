

package org.kinotic.core.api.service;

import org.kinotic.core.api.ServiceRegistry;

import java.util.Map;

/**
 * The {@link FunctionInstanceProvider} provides object instances for {@link FunctionDescriptor}'s
 * This allows the {@link FunctionDescriptor#invocationMethod()} to be invoked with the proper object instance
 *
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
public interface FunctionInstanceProvider {

    /**
     * This method will be called by the {@link ServiceRegistry}
     * for all {@link FunctionDescriptor}'s that are part of any registered {@link ServiceDescriptor}
     * The provider will be called when the {@link ServiceDescriptor} is registered
     *
     * @param functionDescriptor that the Object instance should be provided for
     * @return the correct Object instance for the {@link FunctionDescriptor}
     */
    Object provideInstance(FunctionDescriptor functionDescriptor);


    static FunctionInstanceProvider create(Object instance){
        return functionDescriptor -> instance;
    }

    static FunctionInstanceProvider create(Map<FunctionDescriptor, Object> functionMap){
        return functionMap::get;
    }

}
