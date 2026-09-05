package org.kinotic.idl.api.schema;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a value of any type: the schema intentionally places no constraint on the shape.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AnyC3Type extends C3Type {
}
