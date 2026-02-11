package org.kinotic.persistence.internal.endpoints.openapi;

import io.swagger.v3.core.util.ObjectMapperFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.Nullable;
import org.kinotic.continuum.api.security.SecurityService;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.rpc.gateway.api.security.AuthenticationHandler;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.*;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.MapParameterHolder;
import org.kinotic.persistence.internal.utils.VertxWebUtil;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Created by Navíd Mitchell 🤪 on 5/6/24.
 */
@Component
public class OpenApiVertxRouterFactory {

    private static final com.fasterxml.jackson.databind.ObjectMapper openApiMapper;

    static {
        // Specific serializers are added to the ObjectMapper by the swagger implementation
        openApiMapper = ObjectMapperFactory.createJson();
    }

    private final String adminApiBasePath;
    private final String apiBasePath;
    private final EntitiesService entitiesService;
    private final ObjectMapper objectMapper;
    private final OpenApiService openApiService;
    private final StructuresProperties properties;
    private final SecurityService securityService;
    private final JavaType stringListType;
    private final JavaType tenantSpecificListType;
    private final Vertx vertx;

    public OpenApiVertxRouterFactory(EntitiesService entitiesService,
                                     ObjectMapper objectMapper,
                                     OpenApiService openApiService,
                                     StructuresProperties properties,
                                     SecurityService securityService,
                                     Vertx vertx) {
        this.entitiesService = entitiesService;
        this.objectMapper = objectMapper;
        this.openApiService = openApiService;
        this.properties = properties;
        this.securityService = securityService;
        this.vertx = vertx;

        apiBasePath = properties.getOpenApiPath();
        adminApiBasePath = properties.getOpenApiAdminPath();

        TypeFactory typeFactory = this.objectMapper.getTypeFactory();
        stringListType = typeFactory.constructCollectionType(List.class, String.class);

        tenantSpecificListType = typeFactory.constructCollectionType(List.class, DefaultTenantSpecificId.class);
    }

    private static String extractQueryAndTenantSelectionIfNeeded(RequestBody body, EntityContext ec, boolean admin) {
        String query;
        if(admin){
            QueryWithTenantSelection qwts = body.asPojo(QueryWithTenantSelection.class);
            query = qwts.query();
            ec.setTenantSelection(qwts.tenantSelection());
        }else{
            query = body.asString();
        }
        Validate.notBlank(query, "A request body containing a query must be provided");
        return query;
    }

    public Router createRouter() {
        Router router = VertxWebUtil.createRouterWithCors(vertx, properties);

        BodyHandler bodyHandler = BodyHandler.create(false);
        bodyHandler.setBodyLimit(properties.getMaxHttpBodySize());

        // Open API Docs
        router.get("/api-docs/:structureApplication/openapi.json")
              .produces("application/json")
              .handler(ctx -> {

                  String structureApplication = ctx.pathParam("structureApplication");
                  Validate.notNull(structureApplication, "structureApplication must not be null");

                  Future.fromCompletionStage(openApiService.getOpenApiSpec(structureApplication), vertx.getOrCreateContext())
                        .onComplete((result, failure) -> {
                            if(failure == null){
                                try {
                                    byte[] bytes = openApiMapper.writeValueAsBytes(result);
                                    ctx.response().putHeader("Content-Type", "application/json");
                                    ctx.response().end(Buffer.buffer(bytes));
                                } catch (IOException e) {
                                    VertxWebUtil.writeException(ctx, e);
                                }
                            }else{
                                VertxWebUtil.writeException(ctx, failure);
                            }
                        });
              });

        if (securityService != null) {
            router.route().handler(new AuthenticationHandler(securityService, vertx));
        }

        addDeleteRoutes(router, bodyHandler, true);

        addReadRoutes(router, bodyHandler, true);

        addDeleteRoutes(router, bodyHandler, false);

        addReadRoutes(router, bodyHandler, false);

        addNamedQueryRoutes(router, bodyHandler);

        addCreateUpdateRoutes(router, bodyHandler);

        return router;
    }

    private void addCreateUpdateRoutes(Router router,
                                       BodyHandler bodyHandler) {
        // Bulk save
        router.post(apiBasePath + ":structureApplication/:structureName/bulk")
              .consumes("application/json")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                  handleNoReturnValue(ctx, () ->
                          entitiesService.bulkSave(structureId,
                                                   new RawJson(ctx.body().buffer().getBytes()),
                                                   new RoutingContextToEntityContextAdapter(ctx))
                  );

              });

        // Bulk Update
        router.post(apiBasePath + ":structureApplication/:structureName/bulk-update")
              .consumes("application/json")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                  handleNoReturnValue(ctx, () ->
                          entitiesService.bulkUpdate(structureId,
                                                     new RawJson(ctx.body().buffer().getBytes()),
                                                     new RoutingContextToEntityContextAdapter(ctx))
                  );

              });

        // Update entity
        router.post(apiBasePath + ":structureApplication/:structureName/update")
              .consumes("application/json")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                  handleWithReturnValue(ctx, () ->
                          entitiesService.update(structureId,
                                                 new RawJson(ctx.body().buffer().getBytes()),
                                                 new RoutingContextToEntityContextAdapter(ctx))
                  );

              });

        // Save entity
        router.post(apiBasePath + ":structureApplication/:structureName")
              .consumes("application/json")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                  handleWithReturnValue(ctx, () ->
                          entitiesService.save(structureId,
                                               new RawJson(ctx.body().buffer().getBytes()),
                                               new RoutingContextToEntityContextAdapter(ctx))
                  );

              });

        // Sync Structure
        router.get(apiBasePath + ":structureApplication/:structureName/util/sync")
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                  handleNoReturnValue(ctx, () ->
                          entitiesService.syncIndex(structureId,
                                                    new RoutingContextToEntityContextAdapter(ctx))
                  );

              });
    }

    private void addDeleteRoutes(Router router, BodyHandler bodyHandler, boolean admin) {

        if(admin){
            // Admin Delete Entity By ID
            router.delete(adminApiBasePath + ":structureApplication/:structureName/:tenantId/:id")
                  .handler(ctx -> {

                      String id = ctx.pathParam("id");
                      Validate.notBlank(id, "id must not be null or blank");
                      String tenantID = ctx.pathParam("tenantId");
                      Validate.notBlank(tenantID, "tenantId must not be null or blank");

                      String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                      handleNoReturnValue(ctx, () ->
                              entitiesService.deleteById(structureId,
                                                         TenantSpecificId.create(id, tenantID),
                                                         new RoutingContextToEntityContextAdapter(ctx))
                      );

                  });
        }else{
            // Delete Entity By ID
            router.delete(apiBasePath + ":structureApplication/:structureName/:id")
                  .handler(ctx -> {

                      String id = ctx.pathParam("id");
                      Validate.notNull(id, "id must not be null");

                      String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                      handleNoReturnValue(ctx, () ->
                              entitiesService.deleteById(structureId,
                                                         id,
                                                         new RoutingContextToEntityContextAdapter(ctx))
                      );
                  });
        }

        // Delete Entity By Query
        router.post((admin ? adminApiBasePath : apiBasePath) + ":structureApplication/:structureName/delete/by-query")
              .consumes((admin ? "application/json" : "text/plain"))
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  String query;
                  EntityContext ec = new RoutingContextToEntityContextAdapter(ctx);

                  query = extractQueryAndTenantSelectionIfNeeded(ctx.body(), ec, admin);

                  handleNoReturnValue(ctx, () ->
                          entitiesService.deleteByQuery(structureId,
                                                        query,
                                                        ec)
                  );
              });
    }

    private void addNamedQueryRoutes(Router router, BodyHandler bodyHandler) {
        // Named Query
        router.post(apiBasePath + ":structureApplication/:structureName/named-query/:queryName")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  String queryName = VertxWebUtil.validateAndReturnPathParam("queryName", ctx);

                  try {

                      handleWithReturnValue(ctx, () ->
                              entitiesService.namedQuery(structureId,
                                                         queryName,
                                                         extractParameters(ctx),
                                                         RawJson.class,
                                                         new RoutingContextToEntityContextAdapter(ctx))
                      );

                  } catch (JacksonException e) {
                      VertxWebUtil.writeException(ctx, e);
                  }
              });

        // Named Query Page
        router.post(apiBasePath + ":structureApplication/:structureName/named-query-page/:queryName")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  String queryName = VertxWebUtil.validateAndReturnPathParam("queryName", ctx);
                  Pageable pageable = VertxWebUtil.getPageableOrDefaultCursorPageable(ctx);

                  try {

                      handleWithReturnValue(ctx, () ->
                              entitiesService.namedQueryPage(structureId,
                                                             queryName,
                                                             extractParameters(ctx),
                                                             pageable,
                                                             RawJson.class,
                                                             new RoutingContextToEntityContextAdapter(ctx))
                      );

                  } catch (JacksonException e) {
                      VertxWebUtil.writeException(ctx, e);
                  }
              });
    }

    private void addReadRoutes(Router router, BodyHandler bodyHandler, boolean admin) {
        String basePath = (admin ? adminApiBasePath : apiBasePath);

        // Find all entities
        Route findAllRoute
                = router.route((admin ? HttpMethod.POST : HttpMethod.GET), basePath + ":structureApplication/:structureName")
                        .produces("application/json");
        if(admin){
            findAllRoute.consumes("application/json")
                        .handler(bodyHandler);
        }
        findAllRoute.handler(ctx -> {

            String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
            Pageable pageable = VertxWebUtil.getPageableOrDefaultOffsetPageable(ctx);
            EntityContext ec = new RoutingContextToEntityContextAdapter(ctx);

            try {
                if(admin){
                    List<String> tenantSelection = objectMapper.readValue(ctx.body().buffer().getBytes(),
                                                                          stringListType);
                    ec.setTenantSelection(tenantSelection);
                }

                handleWithReturnValue(ctx, () ->
                        entitiesService.findAll(structureId,
                                                pageable,
                                                FastestType.class,
                                                ec)
                );

            } catch (JacksonException e) {
                VertxWebUtil.writeException(ctx, e);
            }
        });

        if(admin){
            // Admin Get Entity By ID
            router.get(adminApiBasePath + ":structureApplication/:structureName/:tenantId/:id")
                  .produces("application/json")
                  .handler(ctx -> {

                      String id = ctx.pathParam("id");
                      Validate.notNull(id, "id must not be null");
                      String tenantID = ctx.pathParam("tenantId");
                      Validate.notBlank(tenantID, "tenantId must not be null or blank");

                      String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                      handleWithReturnValue(ctx, () ->
                                                    entitiesService.findById(structureId,
                                                                             TenantSpecificId.create(id, tenantID),
                                                                             FastestType.class,
                                                                             new RoutingContextToEntityContextAdapter(ctx))
                              , true);
                  });
        }else {
            // Get Entity By ID
            router.get(apiBasePath + ":structureApplication/:structureName/:id")
                  .produces("application/json")
                  .handler(ctx -> {

                      String id = ctx.pathParam("id");
                      Validate.notNull(id, "id must not be null");

                      String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);

                      handleWithReturnValue(ctx, () ->
                                                    entitiesService.findById(structureId,
                                                                             id,
                                                                             FastestType.class,
                                                                             new RoutingContextToEntityContextAdapter(ctx))
                              , true);

                  });
        }


        // Get Total Count for entity
        Route countRoute = router.route((admin ? HttpMethod.POST : HttpMethod.GET), basePath + ":structureApplication/:structureName/count/all")
                                 .produces("application/json");
        if(admin){
            countRoute.consumes("application/json")
                      .handler(bodyHandler);
        }
        countRoute.handler(ctx -> {

            String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
            EntityContext ec = new RoutingContextToEntityContextAdapter(ctx);

            try {
                if(admin){
                    List<String> tenantSelection = objectMapper.readValue(ctx.body().buffer().getBytes(),
                                                                          stringListType);
                    ec.setTenantSelection(tenantSelection);
                }

                handleWithCount(ctx, () ->
                        entitiesService.count(structureId,
                                              ec)
                );

            } catch (JacksonException e) {
                VertxWebUtil.writeException(ctx, e);
            }
        });

        // Get Count for query against entity
        router.post(basePath + ":structureApplication/:structureName/count/by-query")
              .consumes((admin ? "application/json" : "text/plain"))
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  String query;
                  EntityContext ec = new RoutingContextToEntityContextAdapter(ctx);

                  query = extractQueryAndTenantSelectionIfNeeded(ctx.body(), ec, admin);

                  handleWithCount(ctx, () ->
                          entitiesService.countByQuery(structureId,
                                                       query,
                                                       ec)
                  );

              });

        // Get Entity By IDs
        router.post(basePath + ":structureApplication/:structureName/find/by-ids")
              .consumes("application/json")
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  try {
                      if(admin){
                          List<TenantSpecificId> ids = this.objectMapper.readValue(ctx.body().buffer().getBytes(), tenantSpecificListType);

                          handleWithReturnValue(ctx, () ->
                                  entitiesService.findByIdsWithTenant(structureId,
                                                                      ids,
                                                                      FastestType.class,
                                                                      new RoutingContextToEntityContextAdapter(ctx))
                          );

                      }else {
                          List<String> ids = this.objectMapper.readValue(ctx.body().buffer().getBytes(), stringListType);

                          handleWithReturnValue(ctx, () -> entitiesService.findByIds(structureId,
                                                                                     ids,
                                                                                     FastestType.class,
                                                                                     new RoutingContextToEntityContextAdapter(ctx))
                          );
                      }
                  } catch (JacksonException e) {
                      VertxWebUtil.writeException(ctx, e);
                  }
              });

        // Search for entities
        router.post(basePath + ":structureApplication/:structureName/search")
              .consumes((admin ? "application/json" : "text/plain"))
              .produces("application/json")
              .handler(bodyHandler)
              .handler(ctx -> {

                  String structureId = VertxWebUtil.validateAndReturnStructureId(ctx);
                  Pageable pageable = VertxWebUtil.getPageableOrDefaultOffsetPageable(ctx);
                  String query;
                  EntityContext ec = new RoutingContextToEntityContextAdapter(ctx);

                  query = extractQueryAndTenantSelectionIfNeeded(ctx.body(), ec, admin);

                  handleWithReturnValue(ctx, () ->
                          entitiesService.search(structureId,
                                                 query,
                                                 pageable,
                                                 FastestType.class,
                                                 ec)
                  );
              });
    }

    private @Nullable ParameterHolder extractParameters(RoutingContext ctx) {
        ParameterHolder parameterHolder = null;
        if(!ctx.body().isEmpty()){
            Map<String, Object> paramMap = this.objectMapper.readValue(ctx.body().buffer().getBytes(), new TypeReference<>() {});
            parameterHolder = new MapParameterHolder(paramMap);
        }

        return parameterHolder;
    }

    private void handleWithCount(RoutingContext ctx, Supplier<CompletableFuture<Long>> supplier){
        Future.fromCompletionStage(supplier.get(),
                                   vertx.getOrCreateContext())
              .onComplete(new CountHandler(ctx))
              .onFailure(throwable -> VertxWebUtil.writeException(ctx, throwable));
    }

    private void handleNoReturnValue(RoutingContext ctx, Supplier<CompletableFuture<Void>> supplier){
        Future.fromCompletionStage(supplier.get(),
                                   vertx.getOrCreateContext())
              .onComplete(new NoValueHandler(ctx))
              .onFailure(throwable -> VertxWebUtil.writeException(ctx, throwable));
    }

    private <T> void handleWithReturnValue(RoutingContext ctx, Supplier<CompletableFuture<T>> supplier){
        handleWithReturnValue(ctx, supplier, false);
    }

    private <T> void handleWithReturnValue(RoutingContext ctx, Supplier<CompletableFuture<T>> supplier, boolean send404IfNull){
        Future.fromCompletionStage(supplier.get(),
                                   vertx.getOrCreateContext())
              .onComplete(new ValueToJsonHandler<>(ctx, objectMapper, send404IfNull))
              .onFailure(throwable -> VertxWebUtil.writeException(ctx, throwable));
    }

}
