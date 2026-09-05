

package org.kinotic.core.api.service;

import org.kinotic.idl.api.utils.IdlUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 9/2/21.
 */
class ReflectiveServiceDescriptor implements ServiceDescriptor{

    private final ServiceIdentifier serviceIdentifier;
    private final Collection<FunctionDescriptor> functionDescriptors;

    /**
     * A {@link ServiceDescriptor} created using reflection
     * @param serviceIdentifier that should be used
     * @param serviceClass the class to introspect for methods to create {@link FunctionDescriptor}'s for
     * @throws IllegalStateException if reflection fails, or if the class overloads a function name
     */
    public ReflectiveServiceDescriptor(ServiceIdentifier serviceIdentifier, Class<?> serviceClass) {
        this.serviceIdentifier = serviceIdentifier;

        // IdlUtil.serviceFunctions is the same walk DefaultSchemaFactory converts, so the functions
        // this descriptor registers are exactly the functions any published schema carries
        List<FunctionDescriptor> functions = new ArrayList<>();
        IdlUtil.serviceFunctions(serviceClass)
               .forEach((name, method) -> functions.add(FunctionDescriptor.create(name, method)));
        this.functionDescriptors = functions;
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
