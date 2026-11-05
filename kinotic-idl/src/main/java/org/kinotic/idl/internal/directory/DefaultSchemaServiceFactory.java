package org.kinotic.idl.internal.directory;

import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.directory.AuthzPermissionResolver;
import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.SchemaServiceFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<AuthzPermissionResolver> permissionResolvers;

    @Override
    public SchemaService create(Set<Class<?>> skippedParameterTypes) {
        // the resolvers are read when services convert, after the beans they depend on exist
        return new DefaultSchemaService(genericTypeConverter, skippedParameterTypes, () -> permissionResolvers.orderedStream().toList());
    }
}
