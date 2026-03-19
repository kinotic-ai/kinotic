package org.kinotic.persistence.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Navíd Mitchell 🤪 on 6/19/24.
 */
@NoArgsConstructor
@Getter
@Setter
public class QueryOptions {

    private String timeZone;
    private Integer requestTimeout;
    private String pageTimeout;

}
