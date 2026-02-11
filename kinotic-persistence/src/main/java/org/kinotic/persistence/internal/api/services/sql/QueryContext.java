package org.kinotic.persistence.internal.api.services.sql;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.domain.ParameterHolder;
import org.kinotic.persistence.api.domain.QueryOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Created By Navíd Mitchell 🤪on 2/24/25
 */
@RequiredArgsConstructor
@Getter
@Setter
public class QueryContext {

    /**
     * The {@link EntityContext} supplied for this query
     */
    private final EntityContext entityContext;

    /**
     * The {@link ParameterHolder} supplied for this query
     */
    private final ParameterHolder parameterHolder;

    /**
     * The queryParameters to be used in the query
     */
    private List<Object> queryParameters = new ArrayList<>();

    /**
     * The options to be used in the query
     */
    private QueryOptions queryOptions;

}
