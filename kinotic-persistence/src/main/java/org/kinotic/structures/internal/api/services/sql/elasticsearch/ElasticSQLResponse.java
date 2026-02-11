package org.kinotic.structures.internal.api.services.sql.elasticsearch;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 4/30/24.
 */
@NoArgsConstructor
@Getter
@Setter
@Accessors(chain = true)
public class ElasticSQLResponse {

    private List<ElasticColumn> columns;

    private List<List<Object>> rows;

    private String cursor;
}
