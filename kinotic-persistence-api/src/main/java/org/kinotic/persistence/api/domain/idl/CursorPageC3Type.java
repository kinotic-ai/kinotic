package org.kinotic.persistence.api.domain.idl;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;

/**
 * This is a placeholder for the Page type.
 * This is not a complete type but a placeholder for the server to know what to expect.
 * Created by Navíd Mitchell 🤪 on 5/7/24.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CursorPageC3Type extends PageC3Type {

    public CursorPageC3Type(ObjectC3Type contentType) {
        super(contentType);
    }
}
