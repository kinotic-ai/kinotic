

package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.annotations.Name;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.springframework.core.MethodParameter;

import java.util.Collection;

/**
 * Provides the ability to create {@link C3Type}'s
 * Created by navid on 2019-06-13.
 */
public interface SchemaFactory {

    /**
     * Returns the name a method parameter carries in contracts built by this factory: the {@link Name} annotation's
     * value when present, otherwise the compiled parameter name. Argument binders for named wire formats must use
     * the same rule, so the emitted schema and the runtime binding cannot drift.
     *
     * @param methodParameter the parameter to name
     * @return the parameter's contract name
     */
    static String parameterName(MethodParameter methodParameter) {
        Name nameAnnotation = methodParameter.getParameterAnnotation(Name.class);
        return nameAnnotation != null ? nameAnnotation.value() : methodParameter.getParameter().getName();
    }

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
     * Creates a {@link NamespaceDefinition} containing a {@link ServiceDefinition} for each given {@link Class},
     * treating each class as a java "service". All services are converted in one session, so complex types shared
     * between services are converted once and appear once in the returned namespace. A service that fails to
     * convert is omitted from the result rather than failing the batch. Each definition's qualified name is the
     * class's package name and simple name joined with {@code '.'}.
     *
     * @param serviceInterfaces the classes to create service definitions for, duplicates ignored
     * @return the newly created {@link NamespaceDefinition} with every converted service and every referenced complex type
     */
    NamespaceDefinition createForServices(Collection<Class<?>> serviceInterfaces);

}
