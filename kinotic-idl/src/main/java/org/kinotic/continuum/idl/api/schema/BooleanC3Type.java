

package org.kinotic.continuum.idl.api.schema;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The boolean type matches only two special values: true and false.
 * Note that values that evaluate to true or false, such as 1 and 0, are not accepted by the schema.
 * <p>
 * Created by navid on 2023-4-13.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BooleanC3Type extends C3Type {
}
