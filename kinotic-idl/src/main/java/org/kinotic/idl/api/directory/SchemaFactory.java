

package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.Map;

/**
 * Provides the ability to create {@link C3Type}'s
 * Created by navid on 2019-06-13.
 */
public interface SchemaFactory {

    /**
     * Creates a {@link C3Type} for the given {@link Class}
     * This method treats the class as a standard POJO or basic type.
     * If you need to convert classes that are "services" use {@link SchemaFactory#createForServices(Map)}
     *
     * @param clazz the class to create the schema for
     * @return the newly created {@link C3Type}
     */
    C3Type createForClass(Class<?> clazz);

    /**
     * Creates a {@link NamespaceDefinition} containing a {@link ServiceDefinition} for each given service. The
     * contract decides which functions the definition carries — one function per method name, overloading is
     * not supported — while each function's parameter names, generic
     * bindings, and annotations resolve against the implementation's most specific method — the same method
     * invoked at runtime. Pass the contract itself as the implementation when no separate implementation exists.
     * All services are converted in one session, so complex types shared between services are converted once and
     * appear once in the returned namespace. A service that fails to convert is omitted from the result rather
     * than failing the batch. Each definition's qualified name is the contract's package name and simple name
     * joined with {@code '.'}.
     *
     * @param services the services to create definitions for, keyed contract to implementation
     * @return the newly created {@link NamespaceDefinition} with every converted service and every referenced complex type
     */
    NamespaceDefinition createForServices(Map<Class<?>, Class<?>> services);

}
