package org.kinotic.persistence.internal.utils;

import java.util.regex.Pattern;

/**
 * Created by Navíd Mitchell 🤪 on 5/5/24.
 */
public class QueryUtils {

    private static final Pattern aggregatePattern = Pattern.compile("\\b(AVG|COUNT|FIRST|LAST|MAX|MIN|SUM|KURTOSIS|MAD|PERCENTILE|PERCENTILE_RANK|SKEWNESS|STDDEV_POP|STDDEV_SAMP|SUM_OF_SQUARES|VAR_POP|VAR_SAMP)\\s*\\([a-zA-Z0-9_.,='() ]+\\)");

    /**
     * Whether a named query's statement is a SELECT that aggregates, which runs on Elasticsearch SQL.
     */
    public static boolean isAggregate(String statement){
        return statement.toLowerCase().startsWith("select") && aggregatePattern.matcher(statement.toUpperCase()).find();
    }

}
