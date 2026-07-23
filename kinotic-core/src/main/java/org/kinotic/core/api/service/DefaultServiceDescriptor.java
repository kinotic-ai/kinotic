

package org.kinotic.core.api.service;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
class DefaultServiceDescriptor implements ServiceDescriptor{

    private final ServiceIdentifier serviceIdentifier;
    private final Collection<FunctionDescriptor> functionDescriptors;

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
        this.functionDescriptors = new ArrayList<>();
    }

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier,
                                    Collection<FunctionDescriptor> functionDescriptors) {
        this.serviceIdentifier = serviceIdentifier;
        this.functionDescriptors = functionDescriptors;
    }

    @Override
    public ServiceIdentifier serviceIdentifier() {
        return serviceIdentifier;
    }

    @Override
    public Collection<FunctionDescriptor> functions() {
        return functionDescriptors;
    }
}
