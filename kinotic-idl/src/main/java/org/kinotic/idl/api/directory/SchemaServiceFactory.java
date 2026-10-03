package org.kinotic.idl.api.directory;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Allows you to configure and create {@link SchemaService} instances.
 * Created By Navíd Mitchell 🤪on 10/2/26
 */
@Component
public interface SchemaServiceFactory {

    /**
     * Creates a new {@link SchemaService} instance.
     * @param skippedParameterTypes the types that should be skipped when generating the schema
     * @return a new {@link SchemaService} instance
     */
    SchemaService create(Set<Class<?>> skippedParameterTypes);

}
