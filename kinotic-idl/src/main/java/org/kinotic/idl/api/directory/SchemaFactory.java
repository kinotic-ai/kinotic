

package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.NamespaceDefinition;

/**
 * Provides the ability to create {@link C3Type}'s
 * Created by navid on 2019-06-13.
 */
public interface SchemaFactory {

    /**
     * Creates a {@link C3Type} for the given {@link Class}
     * This method treats the class as a standard POJO or basic type.
     * If you need to convert a class that is a "service" use {@link SchemaFactory#createForService(Class)}
     *
     * @param clazz the class to create the schema for
     * @return the newly created {@link C3Type}
     */
    C3Type createForClass(Class<?> clazz);

    /**
     * Creates a {@link NamespaceDefinition} for the given {@link Class}
     * This method treats the class as a java "service"
     *
     * @param clazz the class to create the schema for
     * @return the newly created {@link NamespaceDefinition}
     */
    NamespaceDefinition createForService(Class<?> clazz);

}
