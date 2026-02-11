package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Provides functionality to define a parameter for a {@link FunctionDefinition}.
 * The context for equality here is the {@link FunctionDefinition}.
 * Given that no two {@link ParameterDefinition}s can have the same name in the same {@link FunctionDefinition}.
 * Created by navid on 2023-4-13
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class ParameterDefinition extends AbstractDefinition {

    /**
     * The name of this {@link ParameterDefinition}, and the argument name for the {@link FunctionDefinition}
     */
    private String name;

    /**
     * This is the {@link C3Type} that defines the type of this argument.
     */
    @EqualsAndHashCode.Exclude // The context for equality here is the function definition
    private C3Type type;

}
