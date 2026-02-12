

package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Provides functionality to define an interface / service with a Continuum schema.
 * The context for equality here is the {@link NamespaceDefinition}.
 * Given no two service definitions can have the same namespace and name in a {@link NamespaceDefinition}.
 * Created by navid on 2023-4-13.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class ServiceDefinition extends AbstractDefinition implements HasQualifiedName {

    /**
     * The namespace of this {@link ServiceDefinition}
     */
    private String namespace;

    /**
     * The name of this {@link ServiceDefinition}
     */
    private String name;

    /**
     * This defines {@link FunctionDefinition}'s for this {@link ServiceDefinition}
     * The key is the function name and the value is the schema that defines the function
     */
    @EqualsAndHashCode.Exclude
    @JsonDeserialize(as = LinkedList.class)
    private Set<FunctionDefinition> functions = new LinkedHashSet<>();

    /**
     * Stores the given value in the functions definitions for this schema
     * If a schema for the name already exists an error will be thrown
     * @param function {@link FunctionDefinition} defining the function
     * @return this
     */
    public ServiceDefinition addFunction(FunctionDefinition function){
        Validate.isTrue(!functions.contains(function), "ServiceDefinition already contains function "+function);
        functions.add(function);
        return this;
    }

}
