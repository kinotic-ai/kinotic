package org.kinotic.sql.domain;

import java.util.List;

/**
 * A call of an Elasticsearch SQL function, such as {@code COUNT(*)} or {@code HISTOGRAM(age, 10)}.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param name      the function's name, as written
 * @param arguments the arguments, in the order written; empty for a call that takes none
 */
public record FunctionCall(String name, List<FunctionArgument> arguments) implements SelectExpression {
}
