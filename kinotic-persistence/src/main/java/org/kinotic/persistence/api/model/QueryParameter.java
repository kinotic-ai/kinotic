package org.kinotic.persistence.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Represents a query parameter for a query
 * Created by Navíd Mitchell 🤪 on 4/25/24.
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class QueryParameter {
    private String key;
    private Object value;
}
