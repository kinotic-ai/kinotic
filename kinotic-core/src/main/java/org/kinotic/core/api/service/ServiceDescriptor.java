

package org.kinotic.core.api.service;

import java.util.Collection;

/**
 * Describes services that can be registered with the Continuum.
 * This is the node-local, invocable view of a service, holding live {@link FunctionDescriptor}s; the declarative,
 * serializable contract view is {@code ServiceDefinition} in the service directory.
 * Created by Navíd Mitchell 🤪 on 7/18/21.
 */
public interface ServiceDescriptor {

    /**
     * This identifies the {@link ServiceDescriptor}
     * @return the {@link ServiceIdentifier} for this
     */
    ServiceIdentifier serviceIdentifier();

    /**
     * All of the {@link FunctionDescriptor}'s for this {@link ServiceDescriptor}
     * @return the list of functions
     */
    Collection<FunctionDescriptor> functions();

    static ServiceDescriptor create(ServiceIdentifier serviceIdentifier){
        return new DefaultServiceDescriptor(serviceIdentifier);
    }

    static ServiceDescriptor create(ServiceIdentifier serviceIdentifier, Collection<FunctionDescriptor> functionDescriptors){
        return new DefaultServiceDescriptor(serviceIdentifier, functionDescriptors);
    }

    /**
     * Creates a {@link ServiceDescriptor} using refelction
     * @param serviceIdentifier to use for the {@link ServiceDescriptor}
     * @param serviceClass to use to determine which {@link FunctionDescriptor}'s should exist
     * @return the new {@link ServiceDescriptor}
     */
    static ServiceDescriptor create(ServiceIdentifier serviceIdentifier, Class<?> serviceClass){
        return new ReflectiveServiceDescriptor(serviceIdentifier, serviceClass);
    }

}
