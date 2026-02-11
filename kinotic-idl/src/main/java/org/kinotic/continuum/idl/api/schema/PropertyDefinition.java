package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Defines a property for a {@link ObjectC3Type}
 * The context for equality here is the {@link ObjectC3Type}.
 * Given no two properties can have the same name in an {@link ObjectC3Type}.
 * Created by Navíd Mitchell 🤪 on 2/22/24.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class PropertyDefinition extends AbstractDefinition {

    /**
     * This is the name of the {@link PropertyDefinition} such as "firstName", "lastName"
     */
    private String name = null;

    /**
     * This is the {@link C3Type} of this {@link PropertyDefinition}
     */
    @EqualsAndHashCode.Exclude
    private C3Type type = null;

}
