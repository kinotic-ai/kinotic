package org.mindignited.structures.api.domain.idl;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.C3Type;
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
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PageC3Type extends C3Type {

    /**
     * The {@link ObjectC3Type} of the content of the page
     * The actual page content will be a list of this type
     */
    @ToString.Exclude
    private ObjectC3Type contentType = null;

}
