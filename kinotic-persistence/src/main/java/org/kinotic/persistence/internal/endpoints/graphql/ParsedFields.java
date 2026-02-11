package org.kinotic.persistence.internal.endpoints.graphql;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 12/14/23.
 */
@NoArgsConstructor
@Getter
public class ParsedFields {
    /**
     * Content fields are any fields in the selection set that are inside content:{}
     * This is done to make handling of pageable response easy.
     * If we need to handle many more response shapes than pageable this will need to be reworked.
     */
    private final List<String> contentFields = new ArrayList<>();

    /**
     * Non-Content fields are fields not inside of content:{} such as totalPages ect..
     */
    private final List<String> nonContentFields = new ArrayList<>();
}
