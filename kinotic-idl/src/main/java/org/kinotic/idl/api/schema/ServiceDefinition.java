

package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Provides functionality to define an interface / service with a Kinotic schema.
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

    /** More functions than any service declares, which bounds the set built from a definition received over the wire. */
    public static final int MAX_FUNCTIONS = 1000;

    /**
     * This defines {@link FunctionDefinition}'s for this {@link ServiceDefinition}
     * The key is the function name and the value is the schema that defines the function
     */
    @EqualsAndHashCode.Exclude
    private Set<FunctionDefinition> functions = new LinkedHashSet<>();

    /**
     * Replaces the functions of this {@link ServiceDefinition}, at most {@link #MAX_FUNCTIONS} of them.
     * @param functions the functions, in the order they are kept
     * @return this
     * @throws IllegalArgumentException if more than {@link #MAX_FUNCTIONS} functions are given
     */
    public ServiceDefinition setFunctions(Collection<FunctionDefinition> functions) {
        Validate.notNull(functions, "functions must not be null");
        // the count is checked before the set is built, so a definition received over the wire cannot make the
        // decoder hash an unbounded number of entries
        Validate.isTrue(functions.size() <= MAX_FUNCTIONS, "A service declares at most %d functions, %d were given",
                        MAX_FUNCTIONS, functions.size());
        this.functions = new LinkedHashSet<>(functions);
        return this;
    }

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
