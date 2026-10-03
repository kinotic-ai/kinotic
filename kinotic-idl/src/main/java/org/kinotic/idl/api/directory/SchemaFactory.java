

package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.Collection;

/**
 * Provides the ability to create {@link C3Type}'s
 * Created by navid on 2019-06-13.
 */
public interface SchemaFactory {

    /**
     * Creates a {@link C3Type} for the given {@link Class}
     * This method treats the class as a standard POJO or basic type.
     * If you need to convert classes that are "services" use {@link SchemaFactory#createForServices(Collection)}
     *
     * @param clazz the class to create the schema for
     * @return the newly created {@link C3Type}
     */
    C3Type createForClass(Class<?> clazz);

    /**
     * Creates a {@link NamespaceDefinition} containing a {@link ServiceDefinition} for each given
     * {@link ServiceDeclaration}. The interface decides which functions the definition carries — one function
     * per method name, an interface that overloads one is rejected — and each function's parameter names, so
     * the published names are the ones named-argument binding resolves at invocation. Generic bindings and
     * annotations resolve against the implementation's most specific method.
     * A parameter of a type the {@link SkippedParameterTypes} beans name is left out of its function, as the
     * request leaves it out. A service declaring {@code @AuthzResource} carries its resource decorator, and each
     * of its functions the check derived from its name, parameters and {@code @AuthzCheck}.
     * All services are converted in one session, so complex types shared between services are converted once and
     * appear once in the returned namespace. Equal declarations convert once, and a service that fails to
     * convert fails the call, so a caller never publishes a contract that is missing a service. Each
     * definition's qualified name is the interface's package name and simple name joined with {@code '.'}.
     *
     * @param services the services to create definitions for
     * @return the newly created {@link NamespaceDefinition} with every converted service and every referenced complex type
     * @throws IllegalStateException    when a service overloads a function, a signature cannot be described to a
     *                                  wire consumer, or an authorization declaration of a resource service does
     *                                  not resolve
     * @throws IllegalArgumentException when a type in a signature has no converter
     */
    NamespaceDefinition createForServices(Collection<ServiceDeclaration> services);

}
