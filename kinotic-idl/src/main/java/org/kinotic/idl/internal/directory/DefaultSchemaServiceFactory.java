package org.kinotic.idl.internal.directory;

import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.SchemaServiceFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Default implementation of {@link SchemaServiceFactory}.
 * Created By Navíd Mitchell 🤪on 10/2/26
 */
@Component
@RequiredArgsConstructor
public class DefaultSchemaServiceFactory implements SchemaServiceFactory {

    private final GenericTypeConverter genericTypeConverter;

    @Override
    public SchemaService create(Set<Class<?>> skippedParameterTypes) {
        return new DefaultSchemaService(genericTypeConverter, skippedParameterTypes);
    }
}
