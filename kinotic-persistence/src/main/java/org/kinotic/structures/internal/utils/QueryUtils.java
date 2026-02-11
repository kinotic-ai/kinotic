package org.kinotic.structures.internal.utils;

import org.kinotic.structures.internal.api.services.sql.SqlQueryType;

import java.util.regex.Pattern;

/**
 * Created by Navíd Mitchell 🤪 on 5/5/24.
 */
public class QueryUtils {

    private static final Pattern aggregatePattern = Pattern.compile("\\b(AVG|COUNT|FIRST|LAST|MAX|MIN|SUM|KURTOSIS|MAD|PERCENTILE|PERCENTILE_RANK|SKEWNESS|STDDEV_POP|STDDEV_SAMP|SUM_OF_SQUARES|VAR_POP|VAR_SAMP)\\s*\\([a-zA-Z0-9_.,='() ]+\\)");

    public static SqlQueryType determineQueryType(String query){
        if(query.toLowerCase().startsWith("select")) {
            if(aggregatePattern.matcher(query.toUpperCase()).find()){
                return SqlQueryType.AGGREGATE;
            }else {
                return SqlQueryType.SELECT;
            }
        }else if(query.toLowerCase().startsWith("update")) {
            return SqlQueryType.UPDATE;
        }else if(query.toLowerCase().startsWith("delete")) {
            return SqlQueryType.DELETE;
        }else if(query.toLowerCase().startsWith("insert")) {
            return SqlQueryType.INSERT;
        }else {
            throw new IllegalArgumentException("Unsupported statement " + query);
        }
    }

}
