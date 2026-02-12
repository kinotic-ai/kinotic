package org.kinotic.persistence.internal.api.services.sql.executors;

import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.kinotic.continuum.idl.api.schema.ParameterDefinition;
import org.kinotic.persistence.api.domain.ParameterHolder;
import org.kinotic.persistence.api.domain.QueryOptions;
import org.kinotic.persistence.api.domain.QueryParameter;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.PageableC3Type;
import org.kinotic.persistence.api.domain.idl.QueryOptionsC3Type;
import org.kinotic.persistence.api.domain.idl.TenantSelectionC3Type;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.kinotic.persistence.internal.api.services.sql.MapParameterHolder;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.persistence.internal.api.services.sql.QueryMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link QueryExecutor} that extracts special parameters from the {@link QueryContext} before delegating to another {@link QueryExecutor}
 *
 * Created By Navíd Mitchell 🤪on 2/24/25
 */

public class ParameterProcessorExecutor extends AbstractQueryExecutor {

    private static final String pageTimeoutOptionName = "queryPageTimeout".toLowerCase();
    private static final String requestTimeoutOptionName = "queryRequestTimeout".toLowerCase();
    private static final String timeZoneOptionName = "queryTimeZone".toLowerCase();
    private final QueryExecutor delegate;
    private final QueryMetadata queryMetadata;

    public ParameterProcessorExecutor(Structure structure,
                                      FunctionDefinition namedQueryDefinition,
                                      QueryExecutor delegate) {
        super(structure);
        this.delegate = delegate;
        this.queryMetadata = buildQueryMetadata(namedQueryDefinition);
    }

    @Override
    public <T> CompletableFuture<List<T>> execute(QueryContext context, Class<T> type) {
        processQueryContext(context);
        return delegate.execute(context, type);
    }

    @Override
    public <T> CompletableFuture<Page<T>> executePage(QueryContext context, Pageable pageable, Class<T> type) {
        processQueryContext(context);
        return delegate.executePage(context, pageable, type);
    }

    private void addToQueryMetadata(ParameterDefinition parameterDefinition,
                                    QueryMetadata.QueryMetadataBuilder queryMetadataBuilder){

        String property = parameterDefinition.getName();
        if(parameterDefinition.getType() instanceof TenantSelectionC3Type) {
            queryMetadataBuilder.tenantSelectionParameterName(property);
        }else if(parameterDefinition.getType() instanceof QueryOptionsC3Type){
            queryMetadataBuilder.queryOptionsParameterName(property);
        }else if(property.toLowerCase().equals(timeZoneOptionName)){
            queryMetadataBuilder.hasTimeZoneOptionParameter(true);
        }else if(property.toLowerCase().equals(requestTimeoutOptionName)){
            queryMetadataBuilder.hasRequestTimeoutOptionParameter(true);
        }else if(property.toLowerCase().equals(pageTimeoutOptionName)){
            queryMetadataBuilder.hasPageTimeoutOptionParameter(true);
        }
        queryMetadataBuilder.queryParameterName(property);
    }

    private QueryMetadata buildQueryMetadata(FunctionDefinition namedQueryDefinition){
        QueryMetadata.QueryMetadataBuilder queryMetadataBuilder = QueryMetadata.builder();
        if(!namedQueryDefinition.getParameters().isEmpty()){

            for(ParameterDefinition definition : namedQueryDefinition.getParameters()) {

                if(!(definition.getType() instanceof PageableC3Type)){
                    addToQueryMetadata(definition, queryMetadataBuilder);
                }
            }
        }
        return queryMetadataBuilder.build();
    }

    // TODO: add performance improvements for using the index when using a list.
    //       and for the map extract special params then the rest.
    //       This way we do not do a bunch of string comparisons during the query request
    private void addPropertyToContext(String property,
                                      Object value,
                                      QueryContext context) {
        if(property.equals(queryMetadata.getTenantSelectionParameterName())) {
            if (!(value instanceof List<?> list)) {
                throw new IllegalArgumentException("Tenant selection must be a List");
            }
            @SuppressWarnings("unchecked")
            List<String> tenantList = (List<String>) list;
            context.getEntityContext().setTenantSelection(tenantList);
        }else if(property.equals(queryMetadata.getQueryOptionsParameterName())) {
            context.setQueryOptions((QueryOptions) value);
        }else if(property.toLowerCase().equals(timeZoneOptionName)){
            if(context.getQueryOptions() == null){
                context.setQueryOptions(new QueryOptions());
            }
            context.getQueryOptions().setTimeZone((String)value);
        }else if(property.toLowerCase().equals(requestTimeoutOptionName)){
            if(context.getQueryOptions() == null){
                context.setQueryOptions(new QueryOptions());
            }
            context.getQueryOptions().setRequestTimeout((Integer)value);
        }else if(property.toLowerCase().equals(pageTimeoutOptionName)){
            if(context.getQueryOptions() == null){
                context.setQueryOptions(new QueryOptions());
            }
            context.getQueryOptions().setPageTimeout((String)value);
        }else{
            context.getQueryParameters().add(value);
        }
    }

    private void processQueryContext(QueryContext context){
        if(queryMetadata.queryExpectsParameters()) {
            ParameterHolder parameterHolder = context.getParameterHolder();
            Validate.notNull(parameterHolder, "parameters must not be null");

            if(parameterHolder instanceof ListParameterHolder listParameterHolder) {

                List<QueryParameter> queryParameters = listParameterHolder.getParameters();

                // for now, we will just return the parameters in the order they are in the list.
                // We may need to use the parameterNames list to reorder them.
                // So far the javascript client provides the order of the parameters in the list is the same as the order of the parameterNames list
                for (QueryParameter queryParameter : queryParameters) {
                    addPropertyToContext(queryParameter.getKey(), queryParameter.getValue(), context);
                }
            }else if(parameterHolder instanceof MapParameterHolder mapParameterHolder){

                Map<String, Object> queryParameters = mapParameterHolder.getParameters();

                for(String key : queryMetadata.getQueryParameterNames()){
                    Object value = queryParameters.get(key);
                    if(value != null){
                        addPropertyToContext(key, value, context);
                    }else{
                        throw new IllegalArgumentException("Parameter " + key + " is missing from expected parameters");
                    }
                }
            }else {
                throw new IllegalArgumentException("Unsupported ParameterHolder type: " + parameterHolder.getClass().getName());
            }

        }
    }

}
