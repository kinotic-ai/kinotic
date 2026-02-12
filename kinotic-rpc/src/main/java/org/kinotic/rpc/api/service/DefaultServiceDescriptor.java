

package org.kinotic.rpc.api.service;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
class DefaultServiceDescriptor implements ServiceDescriptor{

    private final ServiceIdentifier serviceIdentifier;
    private final Collection<ServiceFunction> serviceFunctions;

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
        this.serviceFunctions = new ArrayList<>();
    }

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier,
                                    Collection<ServiceFunction> serviceFunctions) {
        this.serviceIdentifier = serviceIdentifier;
        this.serviceFunctions = serviceFunctions;
    }

    @Override
    public ServiceIdentifier serviceIdentifier() {
        return serviceIdentifier;
    }

    @Override
    public Collection<ServiceFunction> functions() {
        return serviceFunctions;
    }
}
