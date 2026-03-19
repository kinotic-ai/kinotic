package org.kinotic.persistence.internal.api.services.sql.elasticsearch;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by Navíd Mitchell 🤪 on 4/30/24.
 */
@NoArgsConstructor
@Getter
@Setter
@Accessors(chain = true)
public class ElasticColumn {

    private String name;
    private String type;

}
