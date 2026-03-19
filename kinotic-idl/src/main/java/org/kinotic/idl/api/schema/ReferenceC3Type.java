

package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * A simple Type to allow referencing other components in the specification, internally and externally.
 * Created by navid on 2023-4-13.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReferenceC3Type extends C3Type {

    /**
     * The fully qualified name of the schema being referenced
     */
    private String qualifiedName = null;

}
