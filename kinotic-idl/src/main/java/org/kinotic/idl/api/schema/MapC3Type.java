

package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Defines a map of key value pairs
 * Created by navid on 2023-4-13.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MapC3Type extends C3Type {

    /**
     * The type of the defined maps keys
     */
    private C3Type key;

    /**
     * The type of the defined maps values
     */
    private C3Type value;

}
