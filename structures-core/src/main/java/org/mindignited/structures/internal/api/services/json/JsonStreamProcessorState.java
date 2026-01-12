package org.mindignited.structures.internal.api.services.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
@NoArgsConstructor
@Accessors(chain = true, fluent = true)
@Getter
@Setter
public class JsonStreamProcessorState {

    private String currentFieldName = null;
    private String currentJsonPath = null;


}
