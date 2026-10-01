package org.kinotic.sql.executor;

import java.util.List;

/**
 * A statement in Elasticsearch SQL with the values its {@code ?} placeholders take.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param statement  the statement text
 * @param parameters the value of each placeholder, in placeholder order; a
 *                   {@link org.kinotic.sql.domain.NamedParameter} is supplied when the statement is executed, see
 *                   {@link ParameterUtils#bind}
 */
public record ElasticsearchSql(String statement, List<Object> parameters) {
}
