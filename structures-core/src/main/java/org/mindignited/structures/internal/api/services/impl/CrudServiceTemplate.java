package org.mindignited.structures.internal.api.services.impl;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.JsonpMapperBase;
import co.elastic.clients.transport.JsonEndpoint;
import co.elastic.clients.transport.endpoints.EndpointWithResponseMapperAttr;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;
import org.apache.commons.lang3.Validate;
import org.mindignited.continuum.core.api.crud.*;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.internal.serializer.RawJsonJsonpDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by Navíd Mitchell 🤪 on 5/10/23.
 */
@Component
public class CrudServiceTemplate {

    private static final long DEFAULT_PRIORITY = 500L;

    private static final Logger log = LoggerFactory.getLogger(CrudServiceTemplate.class);

    private final ElasticsearchAsyncClient esAsyncClient;
    private final ObjectMapper objectMapper;
    private final RawJsonJsonpDeserializer rawJsonJsonpDeserializer;

    public CrudServiceTemplate(ElasticsearchAsyncClient esAsyncClient,
                               ObjectMapper objectMapper) {
        this.esAsyncClient = esAsyncClient;
        this.objectMapper = objectMapper;
        rawJsonJsonpDeserializer = new RawJsonJsonpDeserializer(objectMapper);
    }


    // In CrudServiceTemplate.java

    /**
     * Counts the number of documents in the index. Also allows for customization of the {@link CountRequest}.
     *
     * @param indexName       name of the index to count
     * @param builderConsumer to customize the {@link CountRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with the number of documents in the index
     */
    public CompletableFuture<Long> count(String indexName,
                                         Consumer<CountRequest.Builder> builderConsumer) {
        return esAsyncClient.count(builder -> {
                                builder.index(indexName);
                                if (builderConsumer != null) {
                                    builderConsumer.accept(builder);
                                }
                                return builder;
                            })
                            .thenApply(CountResponse::count);
    }

    /**
     * Creates a data stream
     */
    public CompletableFuture<Void> createDataStream(String dataStreamName) {
        return esAsyncClient.indices().createDataStream(builder -> builder.name(dataStreamName))
                            .thenApply(response -> null);
    }

    /**
     * Creates an index with the given name. Also allows for customization of the {@link CreateIndexRequest}.
     *
     * @param indexName       name of the index to create
     * @param failIfExists    if true will fail with an exception if the index already exists
     * @param mappings        the mappings to use for the index, or null if no mappings are needed
     * @return a {@link CompletableFuture} that will complete when the index has been created
     */
    public CompletableFuture<Void> createIndex(String indexName,
                                               boolean failIfExists,
                                               Map<String, Property> mappings) {
        return esAsyncClient.indices().exists(builder -> builder.index(indexName))
                            .thenCompose(exists -> {
                                if (!exists.value()) {
                                    return esAsyncClient.indices()
                                                        .create(builder -> {
                                                            builder.index(indexName)
                                                                   .settings(s -> s
                                                                           .numberOfShards("3")
                                                                           .numberOfReplicas("2")
                                                                           .store(st -> st.type(StorageType.Fs))
                                                                   );
                                                            if (mappings != null && !mappings.isEmpty()) {
                                                                builder.mappings(m -> m
                                                                        .dynamic(DynamicMapping.Strict)
                                                                        .properties(mappings));
                                                            }
                                                            return builder;
                                                        })
                                                        .thenApply(response -> null);
                                } else {
                                    if (failIfExists) {
                                        return CompletableFuture.failedFuture(
                                                new IllegalArgumentException("Index already exists: " + indexName));
                                    } else {
                                        return CompletableFuture.completedFuture(null);
                                    }
                                }
                            });
    }

    /**
     * Creates an index template with the given name, pattern, and mappings
     * @param templateName the name of the template
     * @param indexPattern the pattern to match the index names
     * @param dataStreamVisibility the visibility of the data stream or null if not a data stream
     * @param mappings the mappings to use for the index, or null if no mappings are needed
     * @return a {@link CompletableFuture} that will complete when the index template has been created
     */
    public CompletableFuture<Void> createIndexTemplate(String templateName,
                                                       String indexPattern,
                                                       DataStreamVisibility dataStreamVisibility,
                                                       Map<String, Property> mappings) {
        Validate.notNull(templateName, "templateName cannot be null");
        Validate.notNull(indexPattern, "indexPattern cannot be null");
        return esAsyncClient.indices().putIndexTemplate(builder -> {
            builder.name(templateName)
                   .indexPatterns(List.of(indexPattern))
                   .priority(DEFAULT_PRIORITY)
                   .create(true)
                   .template(t -> {
                                 t.settings(s -> s
                                         .numberOfShards("3")
                                         .numberOfReplicas("2")
                                 );
                                 if(mappings != null && !mappings.isEmpty()) {
                                     t.mappings(m -> m
                                             .dynamic(DynamicMapping.Strict)
                                             .properties(mappings));
                                 }
                                 return t;
                             }
                   );
            if (dataStreamVisibility != null) {
                builder.dataStream(dataStreamVisibility);
            }
            return builder;
        }).thenApply(response -> null);
    }

    /**
     * Deletes a document by id. Also allows for customization of the {@link DeleteRequest}.
     *
     * @param indexName       name of the index to delete from
     * @param id              of the document to delete
     * @param builderConsumer to customize the {@link DeleteRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with the {@link DeleteResponse}
     */
    public CompletableFuture<DeleteResponse> deleteById(String indexName,
                                                        String id,
                                                        Consumer<DeleteRequest.Builder> builderConsumer) {
        return esAsyncClient.delete(builder -> {
            builder.index(indexName).id(id);
            if (builderConsumer != null) {
                builderConsumer.accept(builder);
            }
            return builder;
        });
    }

    /**
     * Deletes a list of documents by provided query. Also allows for customization of the {@link DeleteRequest}.
     *
     * @param indexName       name of the index to delete from
     * @param builderConsumer to customize the {@link DeleteRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with the {@link DeleteResponse}
     */
    public CompletableFuture<DeleteByQueryResponse> deleteByQuery(String indexName,
                                                                  Consumer<DeleteByQueryRequest.Builder> builderConsumer) {
        return esAsyncClient.deleteByQuery(builder -> {
            builder.index(indexName);
            if (builderConsumer != null) {
                builderConsumer.accept(builder);
            }
            return builder;
        });
    }

    /**
     * Deletes a data stream
     */
    public CompletableFuture<Void> deleteDataStream(String dataStreamName) {
        return esAsyncClient.indices()
                            .deleteDataStream(builder -> builder.name(dataStreamName))
                            .thenApply(response -> null);
    }

    /**
     * Deletes an index template
     */
    public CompletableFuture<Void> deleteIndexTemplate(String templateName) {
        return esAsyncClient.indices()
                            .deleteIndexTemplate(builder -> builder.name(templateName))
                            .thenApply(response -> null);
    }

    /**
     * Finds a document by id. Also allows for customization of the {@link GetRequest}.
     *
     * @param indexName       name of the index to search
     * @param id              of the document to return
     * @param type            of the document to return
     * @param builderConsumer to customize the {@link GetRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with the document
     */
    public <T> CompletableFuture<T> findById(String indexName,
                                             String id,
                                             Class<T> type,
                                             Consumer<GetRequest.Builder> builderConsumer){
        return findById(indexName, id, type, builderConsumer, null);
    }

    /**
     * Finds a document by id. Also allows for customization of the {@link GetRequest}.
     *
     * @param indexName       name of the index to search
     * @param id              of the document to return
     * @param type            of the document to return
     * @param builderConsumer to customize the {@link GetRequest}, or null if no customization is needed
     * @param resultMapper to map the {@link GetResult} to the desired type or null if the source should be returned directly
     * @return a {@link CompletableFuture} that will complete with the document
     */
    public <T, R> CompletableFuture<R> findById(String indexName,
                                                String id,
                                                Class<T> type,
                                                Consumer<GetRequest.Builder> builderConsumer,
                                                Function<GetResult<T>, R> resultMapper) {

        @SuppressWarnings("unchecked")
        JsonEndpoint<GetRequest, GetResponse<T>, ErrorResponse> endpoint = 
            (JsonEndpoint<GetRequest, GetResponse<T>, ErrorResponse>) GetRequest._ENDPOINT;

        endpoint = new EndpointWithResponseMapperAttr<>(endpoint,
                                                        "co.elastic.clients:Deserializer:_global.get.Response.TDocument",
                                                        getDeserializer(type));

        GetRequest.Builder builder = new GetRequest.Builder();

        builder.index(indexName).id(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }

        //noinspection resource
        return esAsyncClient._transport()
                            .performRequestAsync(builder.build(),
                                                 endpoint,
                                                 esAsyncClient._transportOptions())
                            .thenApply(tGetResponse -> {
                                if(resultMapper != null) {
                                    return resultMapper.apply(tGetResponse);
                                }else{
                                    @SuppressWarnings("unchecked")
                                    R result = (R)tGetResponse.source();
                                    return result;
                                }
                            });
    }

    /**
     * Gets multiple documents for their {@link MultiGetOperation} objects. Also allows for customization of the {@link MgetRequest}.
     * @param getOperations list of {@link MultiGetOperation} to get
     * @param type of the document to return
     * @param builderConsumer to customize the {@link MgetRequest}, or null if no customization is needed
     * @param resultMapper to map the {@link GetResult} to the desired type or null if the source should be returned directly
     * @return a {@link CompletableFuture} that will complete with the documents requested
     */
    public <T, R> CompletableFuture<List<R>> multiGet(List<MultiGetOperation> getOperations,
                                                      Class<T> type,
                                                      Consumer<MgetRequest.Builder> builderConsumer,
                                                      Function<GetResult<T>, R> resultMapper){
        @SuppressWarnings("unchecked")
        JsonEndpoint<MgetRequest, MgetResponse<T>, ErrorResponse> endpoint =
                (JsonEndpoint<MgetRequest, MgetResponse<T>, ErrorResponse>) MgetRequest._ENDPOINT;

        endpoint = new EndpointWithResponseMapperAttr<>(endpoint,
                                                        "co.elastic.clients:Deserializer:_global.mget.Response.TDocument",
                                                        getDeserializer(type));

        MgetRequest.Builder builder = new MgetRequest.Builder();
        builder.docs(getOperations);

        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }

        //noinspection resource
        return esAsyncClient._transport()
                            .performRequestAsync(builder.build(),
                                                 endpoint,
                                                 esAsyncClient._transportOptions())
                            .thenApply(response -> {

                                List<MultiGetResponseItem<T>> recordsResponse = response.docs();
                                ArrayList<R> content = new ArrayList<>();

                                if(resultMapper != null) {
                                    for (MultiGetResponseItem<T> hit : recordsResponse) {
                                        if (hit.isResult() && hit.result().found()) {
                                            content.add(resultMapper.apply(hit.result()));
                                        }
                                    }
                                }else{
                                    for (MultiGetResponseItem<T> hit : recordsResponse) {
                                        if(hit.isResult() && hit.result().found()){
                                            @SuppressWarnings("unchecked")
                                            R result = (R)hit.result().source();
                                            content.add(result);
                                        }
                                    }
                                }
                                return content;
                            });
    }

    /**
     * Provides base functionality to get a {@link Page} of documents from elasticsearch. With the ability to customize the {@link SearchRequest}.
     * NOTE: not all customizations are supported, only the ones that make sense for a {@link Page} of documents.
     * For example aggregations are not supported.
     * This is meant to be used internally by implementors.
     *
     * @param indexName       name of the index to search
     * @param pageable        to use for the search
     * @param type            of the documents to return
     * @param builderConsumer to customize the {@link SearchRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with a {@link Page} of documents
     */
    public <T> CompletableFuture<Page<T>> search(String indexName,
                                                 Pageable pageable,
                                                 Class<T> type,
                                                 Consumer<SearchRequest.Builder> builderConsumer){
        return search(indexName, pageable, type, builderConsumer, null);
    }

    /**
     * Provides base functionality to get a {@link Page} of documents from elasticsearch. With the ability to customize the {@link SearchRequest}.
     * NOTE: not all customizations are supported, only the ones that make sense for a {@link Page} of documents.
     * For example aggregations are not supported.
     * This is meant to be used internally by implementors.
     *
     * @param indexName       name of the index to search
     * @param pageable        to use for the search
     * @param type            of the documents to return
     * @param builderConsumer to customize the {@link SearchRequest}, or null if no customization is needed
     * @param hitMapper       to map the {@link Hit} to the desired type or null if the source should be returned directly
     * @return a {@link CompletableFuture} that will complete with a {@link Page} of documents
     */
    public <T,R> CompletableFuture<Page<R>> search(String indexName,
                                                   Pageable pageable,
                                                   Class<T> type,
                                                   Consumer<SearchRequest.Builder> builderConsumer,
                                                   Function<Hit<T>, R> hitMapper) {

        return searchFullResponse(indexName, pageable, type, builderConsumer)
                .thenApply(response -> {

                    HitsMetadata<T> hitsMetadata = response.hits();
                    List<R> content = new ArrayList<>(hitsMetadata.hits().size());
                    List<FieldValue> lastSort = null;

                    if(hitMapper != null) {
                        for (Hit<T> hit : hitsMetadata.hits()) {
                            content.add(hitMapper.apply(hit));
                            lastSort = hit.sort();
                        }
                    }else {
                        for (Hit<T> hit : hitsMetadata.hits()) {
                            @SuppressWarnings("unchecked")
                            R result = (R)hit.source();
                            content.add(result);
                            lastSort = hit.sort();
                        }
                    }

                    if(pageable instanceof CursorPageable) {
                        String cursor = null;
                        if (lastSort != null) {
                            try {
                                cursor = objectMapper.writeValueAsString(lastSort);
                            } catch(JacksonException e){
                                throw new IllegalStateException("Sort Array could not be serialized to JSON", e);
                            }
                        }
                        return new CursorPage<>(content,
                                                cursor,
                                                null);
                    }else{
                        return new Page<>(content,
                                          Objects.requireNonNull(hitsMetadata.total(),
                                                                 "System Error total hits not available")
                                                 .value());
                    }
                });
    }

    public CompletableFuture<Void> updateIndexMapping(String indexName,
                                                      Map<String, Property> mappings) {
        return esAsyncClient.indices().exists(builder -> builder.index(indexName))
                            .thenCompose(exists -> {
                                if (exists.value()) {
                                    return esAsyncClient.indices()
                                                        .putMapping(builder -> {
                                                            builder.index(indexName);
                                                            if (mappings != null && !mappings.isEmpty()) {
                                                                builder.dynamic(DynamicMapping.Strict)
                                                                       .properties(mappings);
                                                            }
                                                            return builder;
                                                        })
                                                        .thenApply(response -> null);
                                } else {
                                    return CompletableFuture.failedFuture(
                                            new IllegalArgumentException("Index " + indexName + " does not exist"));
                                }
                            });
    }

    /**
     * Updates an existing index template
     */
    public CompletableFuture<Void> updateIndexTemplate(String templateName,
                                                       Map<String, Property> mappings) {
        Validate.notNull(templateName, "templateName cannot be null");
        Validate.notNull(mappings, "mappings cannot be null");
        Validate.notEmpty(mappings, "mappings cannot be empty");

        return esAsyncClient.indices()
                            .existsIndexTemplate(builder -> builder.name(templateName))
                            .thenCompose(exists -> {
                                if (!exists.value()) {
                                    return CompletableFuture.failedFuture(
                                            new IllegalArgumentException("Index template " + templateName + " does not exist"));
                                }

                                // Fetch the existing template
                                return esAsyncClient.indices()
                                                    .getIndexTemplate(builder -> builder.name(templateName))
                                                    .thenCompose(response -> {
                                                        IndexTemplate existingTemplate = response.indexTemplates().getFirst()
                                                                                                 .indexTemplate();

                                                        if (existingTemplate == null) {
                                                            return CompletableFuture.failedFuture(
                                                                    new IllegalStateException("Failed to retrieve template " + templateName));
                                                        }

                                                        // Update the template with existing settings and patterns
                                                        return esAsyncClient.indices()
                                                                            .putIndexTemplate(builder -> {
                                                                                builder.name(templateName)
                                                                                       .indexPatterns(existingTemplate.indexPatterns())
                                                                                       .priority(Objects.requireNonNullElse(existingTemplate.priority(), DEFAULT_PRIORITY));

                                                                                // Preserve data stream configuration if present
                                                                                if (existingTemplate.dataStream() != null) {
                                                                                    builder.dataStream(d -> d);
                                                                                }

                                                                                // Apply existing settings and new mappings
                                                                                builder.template(t -> {
                                                                                    IndexTemplateSummary template = existingTemplate.template();
                                                                                    if (template != null && template.settings() != null) {
                                                                                        t.settings(template.settings());
                                                                                    }
                                                                                    t.mappings(m -> m
                                                                                            .dynamic(DynamicMapping.Strict)
                                                                                            .properties(mappings));
                                                                                    return t;
                                                                                });

                                                                                return builder;
                                                                            })
                                                                            .thenApply(pr -> null);
                                                    });
                            });
    }

    private <T> JsonpDeserializer<T> getDeserializer(Class<T> type) {
        if (RawJson.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            JsonpDeserializer<T> deserializer = (JsonpDeserializer<T>) rawJsonJsonpDeserializer;
            return deserializer;
        }

        // Try the built-in deserializers first to avoid repeated lookups in the Jsonp mapper for client-defined classes
        JsonpDeserializer<T> result = JsonpMapperBase.findDeserializer(type);
        if (result != null) {
            return result;
        }

        return JsonpDeserializer.of(type);
    }

    /**
     * Provides base functionality to get a {@link Page} of documents from elasticsearch. With the ability to customize the {@link SearchRequest}.
     *
     * @param indexName       name of the index to search
     * @param pageable        to use for the search
     * @param type            of the documents to return
     * @param builderConsumer to customize the {@link SearchRequest}, or null if no customization is needed
     * @return a {@link CompletableFuture} that will complete with a {@link SearchResponse} of documents
     */
    private <T> CompletableFuture<SearchResponse<T>> searchFullResponse(String indexName,
                                                                        Pageable pageable,
                                                                        Class<T> type,
                                                                        Consumer<SearchRequest.Builder> builderConsumer) {

        Validate.notNull(indexName, "indexName cannot be null");
        Validate.notNull(pageable, "pageable cannot be null");

        @SuppressWarnings("unchecked")
        JsonEndpoint<SearchRequest, SearchResponse<T>, ErrorResponse> endpoint =
                (JsonEndpoint<SearchRequest, SearchResponse<T>, ErrorResponse>) SearchRequest._ENDPOINT;
        endpoint = new EndpointWithResponseMapperAttr<>(endpoint,
                                                        "co.elastic.clients:Deserializer:_global.search.Response.TDocument",
                                                        getDeserializer(type));

        SearchRequest.Builder builder = new SearchRequest.Builder();

        builder.index(indexName)
               .size(pageable.getPageSize());

        if(pageable instanceof OffsetPageable){

            builder.from(((OffsetPageable)pageable).getPageNumber() * pageable.getPageSize())
                   .trackTotalHits(t -> t.enabled(true));

        } else if (pageable instanceof CursorPageable cursorPageable){

            try {
                if(pageable.getSort() == null || pageable.getSort().isUnsorted()){
                    throw new IllegalArgumentException("When using Cursor based paging you MUST provide a Sort value.");
                }

                String cursorJson = cursorPageable.getCursor();
                // this can be null or empty to indicate the first page
                if(cursorJson != null && !cursorJson.isEmpty()) {
                    TypeFactory typeFactory = objectMapper.getTypeFactory();
                    List<FieldValue> searchAfter = objectMapper.readValue(cursorJson,
                                                                          typeFactory.constructCollectionType(List.class,
                                                                                                              FieldValue.class));
                    builder.searchAfter(searchAfter);
                }
            } catch (JacksonException e) {
                throw new IllegalStateException("Cursor could not be deserialized", e);
            }

        } else {
            throw new IllegalArgumentException("Unsupported Pageable type: "+pageable.getClass().getName());
        }

        if(pageable.getSort() != null) {
            for (Order order : pageable.getSort()) {
                builder.sort(s -> s.field(f -> {
                    String property = order.getProperty();
                    FieldSort.Builder fieldSortBuilder
                            = f.field(property)
                               .order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc);

                    // This is a nested sort, so we must set an additional field
                    // TODO: This must only be applied if the property is a nested field, meaning it has the nested annotation.
//                    if (property.contains(".")) {
//                        String baseField = property.substring(0, property.lastIndexOf("."));
//                        fieldSortBuilder.nested(n -> n.path(baseField));
//                    }

                    return fieldSortBuilder;
                }));
            }
        }

        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }

        SearchRequest request = builder.build();

        if(log.isTraceEnabled()) {
            // wrapped, so toString() will not be called if trace is not enabled
            log.trace("Query: \n {}", request.toString());
        }

        //noinspection resource
        return esAsyncClient._transport()
                            .performRequestAsync(request, endpoint, esAsyncClient._transportOptions());
    }

}
