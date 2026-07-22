package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Represents a stream of values produced asynchronously, emitting zero or more {@link #valueType} results over time.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StreamC3Type extends C3Type {

    /**
     * The {@link C3Type} of each value the stream emits
     */
    private C3Type valueType = null;

}
