package org.kinotic.persistence.internal.api.services.sql.elasticsearch;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.sql.TranslateResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.SimpleJsonpMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableObject;
import org.kinotic.rpc.api.crud.CursorPage;
import org.kinotic.rpc.api.crud.CursorPageable;
import org.kinotic.rpc.api.crud.Page;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.QueryOptions;
import org.kinotic.persistence.api.domain.RawJson;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.api.config.ElasticConnectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to ElasticSearch via Vertx.
 * This was done because the ElasticSearch Java client is missing functionality that we need.
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
@Component
public class DefaultElasticVertxClient implements ElasticVertxClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultElasticVertxClient.class);
    private final ObjectMapper objectMapper;
    private final HttpRequest<Buffer> sqlQueryRequest;
    private final HttpRequest<Buffer> sqlTranslateRequest;
    private final WebClient webClient;
    private final Cache<String, List<ElasticColumn>> columnsCache;


    public DefaultElasticVertxClient(ObjectMapper objectMapper,
                                     StructuresProperties structuresProperties,
                                     Vertx vertx,
                                     DefaultCaffeineCacheFactory cacheFactory) {
        this.objectMapper = objectMapper;
        this.columnsCache = cacheFactory.<String, List<ElasticColumn>>newBuilder()
                                        .name("elasticColumnsCache")
                                        .expireAfterAccess(Duration.ofMinutes(35))
                                        .maximumSize(20_000)
                                        .build();

        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout((int) structuresProperties.getElasticConnectionTimeout().toMillis())
                .setTcpNoDelay(true)
                .setTcpKeepAlive(true)
                .setTracingPolicy(TracingPolicy.IGNORE);

        PoolOptions poolOptions = new PoolOptions()
                .setHttp1MaxSize(500)
                .setHttp2MaxSize(500);

        this.webClient = WebClient.create(vertx, options, poolOptions);

        Validate.notEmpty(structuresProperties.getElasticConnections(), "No Elastic connections defined");

        ElasticConnectionInfo elasticConnectionInfo = structuresProperties.getElasticConnections().getFirst();

        sqlQueryRequest = webClient.post(elasticConnectionInfo.getPort(),
                                         elasticConnectionInfo.getHost(), "/_sql");
        if(elasticConnectionInfo.getScheme().equalsIgnoreCase("https")){
            sqlQueryRequest.ssl(true);
        }
        if(structuresProperties.hasElasticUsernameAndPassword()){
            sqlQueryRequest.basicAuthentication(structuresProperties.getElasticUsername(),
                                                structuresProperties.getElasticPassword());
        }

        sqlTranslateRequest = sqlQueryRequest.copy().uri("/_sql/translate");

    }

    @PreDestroy
    public void destroy(){
        webClient.close();
    }

    @WithSpan
    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Page<T>> querySql(String statement,
                                                   List<?> parameters,
                                                   JsonObject filter,
                                                   QueryOptions options,
                                                   Pageable pageable,
                                                   Class<T> type) {
        JsonObject json = new JsonObject();
        boolean foundCursor = false;
        MutableObject<String> cursorProvided = new MutableObject<>(null);
        if(pageable != null){
            if(pageable instanceof CursorPageable cursorPageable){
                if(StringUtils.isNotBlank(cursorPageable.getCursor())) {
                    foundCursor = true;
                    cursorProvided.setValue(cursorPageable.getCursor());
                    json.put("cursor", cursorPageable.getCursor());
                }else{
                    json.put("fetch_size", pageable.getPageSize());
                }
            }else{
                return CompletableFuture.failedFuture(new IllegalArgumentException("Only CursorPageable is supported for queries containing Aggregations."));
            }
        }

        // Only add the query if we are not using a cursor
        if(!foundCursor){
            json.put("query", statement);
            if(parameters != null) {
                JsonArray paramsJson = new JsonArray();
                for(Object param : parameters){
                    paramsJson.add(param);
                }
                json.put("params", paramsJson);
            }
            if(filter != null){
                json.put("filter", filter);
            }
            if(options != null){
                if(options.getTimeZone() != null){
                    json.put("time_zone", options.getTimeZone());
                }
                if (options.getPageTimeout() != null) {
                    json.put("page_timeout", options.getPageTimeout());
                }else{
                    json.put("page_timeout", "2m");
                }
                if (options.getRequestTimeout() != null) {
                    json.put("request_timeout", options.getRequestTimeout());
                }
            }
        }

        return sqlQueryRequest.sendJsonObject(json)
                              .map(resp -> {
                                  if(resp.statusCode() == 200) {
                                      Buffer buffer = resp.body();
                                      if (RawJson.class.isAssignableFrom(type)) {

                                          return (Page<T>) processBufferToRawJson(buffer, cursorProvided.get());

                                      } else if (Map.class.isAssignableFrom(type)) {

                                          return (Page<T>) processBufferToMap(buffer, cursorProvided.get());

                                      } else {
                                          throw new IllegalArgumentException("Type: " + type.getName() + " is not supported at this time");
                                      }
                                  }else{
                                      throw convertErrorResponse(new ByteArrayInputStream(resp.body().getBytes()));
                                  }
                              }).toCompletionStage()
                              .toCompletableFuture();
    }

    @WithSpan
    @Override
    public CompletableFuture<TranslateResponse> translateSql(String statement,
                                                             List<?> parameters){
        JsonObject json = new JsonObject().put("query", statement);
        if(parameters != null) {
            JsonArray paramsJson = new JsonArray();
            for(Object param : parameters){
                paramsJson.add(param);
            }
            json.put("params", paramsJson);
        }
        return sqlTranslateRequest.sendJsonObject(json)
                                  .map(resp -> {

                                      InputStream input = new ByteArrayInputStream(resp.body().getBytes());

                                      if (resp.statusCode() == 200) {

                                          return TranslateResponse.of(builder -> {
                                              JsonpMapper mapper = SimpleJsonpMapper.INSTANCE; // We don't want to fail on unknown fields
                                              builder.withJson(mapper.jsonProvider().createParser(input), mapper);
                                              return builder;
                                          });
                                      } else {
                                          throw convertErrorResponse(input);
                                      }
                                  }).toCompletionStage()
                                  .toCompletableFuture();
    }

    private IllegalArgumentException convertErrorResponse(InputStream input) {
        ErrorResponse errorResponse = ErrorResponse.of(builder -> {
            JsonpMapper mapper = SimpleJsonpMapper.INSTANCE; // We don't want to fail on unknown fields
            builder.withJson(mapper.jsonProvider().createParser(input), mapper);
            return builder;
        });
        ErrorCause cause = errorResponse.error();
        log.debug("Exception from Elastic SQL: {} {} \n{}", cause.type(), cause.reason(), cause.stackTrace());
        return new IllegalArgumentException("SQL " + cause.type() + " " + cause.reason());
    }

    private Page<Map<String, Object>> processBufferToMap(Buffer buffer, String cursorProvided) {
        ElasticSQLResponse response = objectMapper.readValue(buffer.getBytes(), ElasticSQLResponse.class);
        List<ElasticColumn> elasticColumns = getElasticColumns(response, cursorProvided);
        List<Map<String,Object>> ret = new ArrayList<>(response.getRows().size());

        for(List<Object> row : response.getRows()){
            Map<String, Object> obj = new HashMap<>(response.getRows().size(), 1.5F);

            for(int colIdx = 0; colIdx < row.size(); colIdx++){
                obj.put(elasticColumns.get(colIdx).getName(), row.get(colIdx));
            }
            ret.add(obj);
        }
        // now store columns in cache
        if(response.getCursor() != null){
            columnsCache.put(response.getCursor(), elasticColumns);
        }

        // We only allow each to be used once
        if(cursorProvided != null){
            columnsCache.asMap().remove(cursorProvided);
        }
        return new CursorPage<>(ret, response.getCursor(), null);
    }

    private List<ElasticColumn> getElasticColumns(ElasticSQLResponse response, String cursorProvided) {
        List<ElasticColumn> elasticColumns;
        if(cursorProvided != null){
            elasticColumns = columnsCache.getIfPresent(cursorProvided);
            if(elasticColumns == null){
                throw new IllegalStateException("Cursor has expired");
            }
        }else{
            elasticColumns = response.getColumns();
        }
        return elasticColumns;
    }

    private Page<RawJson> processBufferToRawJson(Buffer buffer, String cursorProvided) {
        ElasticSQLResponse response = objectMapper.readValue(buffer.getBytes(), ElasticSQLResponse.class);
        List<ElasticColumn> elasticColumns = getElasticColumns(response, cursorProvided);
        List<RawJson> ret = new ArrayList<>(response.getRows().size());

        for(List<Object> row : response.getRows()){

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JsonGenerator jsonGenerator = objectMapper.createGenerator(outputStream, JsonEncoding.UTF8);
            jsonGenerator.writeStartObject();

            for(int colIdx = 0; colIdx < row.size(); colIdx++){
                jsonGenerator.writeName(elasticColumns.get(colIdx).getName());
                jsonGenerator.writePOJO(row.get(colIdx));
            }
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
            ret.add(new RawJson(outputStream.toByteArray()));
        }

        // now store columns in cache
        if(response.getCursor() != null){
            columnsCache.put(response.getCursor(), elasticColumns);
        }

        // We only allow each to be used once
        if(cursorProvided != null){
            columnsCache.asMap().remove(cursorProvided);
        }

        return new CursorPage<>(ret, response.getCursor(), null);
    }


}
