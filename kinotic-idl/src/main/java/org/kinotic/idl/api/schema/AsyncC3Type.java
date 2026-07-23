package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Represents a value that is produced asynchronously, resolving to a single {@link #valueType} result.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AsyncC3Type extends C3Type {

    /**
     * The {@link C3Type} of the value the asynchronous invocation resolves to
     */
    private C3Type valueType = null;

}
