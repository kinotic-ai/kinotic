

package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Arrays are used for ordered elements.
 * Created by navid on 2023-4-13.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArrayC3Type extends C3Type {

    /**
     * The type the defined array will contain
     * <p>
     */
    @ToString.Exclude
    private C3Type contains = null;


}
