package org.kinotic.persistence.internal.endpoints.openapi;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.WordUtils;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.idl.api.converter.IdlConverter;
import org.kinotic.continuum.idl.api.schema.*;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.structures.api.domain.NamedQueriesDefinition;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.idl.PageC3Type;
import org.kinotic.structures.api.domain.idl.PageableC3Type;
import org.kinotic.structures.api.domain.idl.decorators.QueryDecorator;
import org.kinotic.structures.api.services.NamedQueriesService;
import org.kinotic.structures.api.services.StructureService;
import org.kinotic.persistence.internal.api.services.StructureConversionService;
import org.kinotic.persistence.api.config.OpenApiSecurityType;
import org.kinotic.persistence.internal.idl.converters.openapi.OpenApiConversionState;
import org.kinotic.persistence.internal.utils.OpenApiUtils;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.kinotic.persistence.internal.api.services.sql.SqlQueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 3/17/23.
 */
@RequiredArgsConstructor
@Component
public class DefaultOpenApiService implements OpenApiService {
    private static final Logger log = LoggerFactory.getLogger(DefaultOpenApiService.class);

    private final NamedQueriesService namedQueriesService;
    private final StructureConversionService structureConversionService;
    private final StructureService structureService;
    private final StructuresProperties structuresProperties;


    private static ApiResponses getDefaultResponses(){
        ApiResponses responses = new ApiResponses();
        responses.put("400", new ApiResponse().description("Bad Request"));
        responses.put("401", new ApiResponse().description("Unauthorized"));
        responses.put("403", new ApiResponse().description("Forbidden"));
        responses.put("404", new ApiResponse().description("Not Found"));
        responses.put("500", new ApiResponse().description("Internal Server Error"));
        return responses;
    }

    @WithSpan
    @Override
    public CompletableFuture<OpenAPI> getOpenApiSpec(@SpanAttribute("applicationId") String applicationId) {
        return structureService
                .findAllPublishedForApplication(applicationId, Pageable.ofSize(100))
                .thenComposeAsync(structures -> {
                    OpenAPI openAPI = new OpenAPI(SpecVersion.V30);

                    Info info = new Info()
                            .title(applicationId + " Structures API")
                            .version("1.0")
                            .description("Provides access to Structures Items for the " + applicationId + " application");
                    openAPI.setInfo(info);

                    openAPI.addServersItem(new Server().url(structuresProperties.getStructuresBaseUrl() + ":"  + structuresProperties.getOpenApiPort()));

                    Components components = new Components();

                    // security scheme
                    if(structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BASIC){
                        SecurityScheme securityScheme = new SecurityScheme();
                        securityScheme.setType(SecurityScheme.Type.HTTP);
                        securityScheme.setScheme("basic");
                        components.addSecuritySchemes("BasicAuth", securityScheme);
                        openAPI.setSecurity(List.of(new SecurityRequirement().addList("BasicAuth")));
                    } else if (structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BEARER) {
                        SecurityScheme securityScheme = new SecurityScheme();
                        securityScheme.setType(SecurityScheme.Type.HTTP);
                        securityScheme.setScheme("bearer");
                        components.addSecuritySchemes("BearerAuth", securityScheme);
                        openAPI.setSecurity(List.of(new SecurityRequirement().addList("BearerAuth")));
                    }

                    Paths paths = new Paths();
                    String basePath = structuresProperties.getOpenApiPath();

                    IdlConverter<Schema<?>, OpenApiConversionState> converter
                            = structureConversionService.createOpenApiConverter();
                    for(Structure structure : structures.getContent()){

                        Schema<?> schema = converter.convert(structure.getEntityDefinition());
                        if(schema instanceof ObjectSchema){

                            components.addSchemas(structure.getName(), schema);

                            if(structure.isMultiTenantSelectionEnabled()){
                                addAdminPathItems(paths, structure);
                            }

                            // Add path items for the structure
                            addDefaultPathItems(paths, structure);

                        }else{
                            log.error("Structure {} EntityDefinition did not convert to an OpenAPI ObjectSchema",
                                      structure.getId());
                        }

                        addNamedQueryPathItems(paths, basePath, structure, converter, components);
                    }

                    // Add all the referenced schemas
                    Map<String, Schema<?>> referencedSchemas = converter.getConversionContext()
                                                                        .state()
                                                                        .getReferencedSchemas();
                    for(Map.Entry<String, Schema<?>> entry : referencedSchemas.entrySet()){
                        components.addSchemas(entry.getKey(), entry.getValue());
                    }

                    ObjectSchema countSchema = new ObjectSchema();
                    countSchema.addProperty("count", new IntegerSchema())
                               .description("Contains the total count of items");
                    components.addSchemas("CountResponse", countSchema);

                    // Add TenantSpecificId schema
                    ObjectSchema tenantSpecificIdSchema = new ObjectSchema();
                    tenantSpecificIdSchema.description("A special Id that is used with Multi-tenant access endpoints")
                                          .addProperty("entityId", new StringSchema().description("The id for the entity"))
                                          .addProperty("tenantId", new StringSchema().description("The tenant id for the entity"));
                    components.addSchemas("TenantSpecificId", tenantSpecificIdSchema);

                    // Add Query with tenant selection schema
                    ObjectSchema queryWithTenantSelectionSchema = new ObjectSchema();
                    queryWithTenantSelectionSchema.description("A special Query object that is used with Multi-tenant access endpoints")
                                                  .addProperty("query", new StringSchema()
                                                          .description("The query text to be used for the operation"))
                                                  .addProperty("tenantSelection",
                                                               OpenApiUtils.createStringArraySchema("The list of tenants to use when executing the Query operation"));
                    components.addSchemas("QueryWithTenantSelection", queryWithTenantSelectionSchema);

                    openAPI.setPaths(paths);
                    openAPI.components(components);
                    return CompletableFuture.completedFuture(openAPI);
                });
    }

    @WithSpan
    private void addAdminPathItems(Paths paths, Structure structure){

        String basePath = structuresProperties.getOpenApiAdminPath();
        String lowercaseApplication = structure.getApplicationId().toLowerCase();
        String lowercaseName = structure.getName().toLowerCase();
        String structureName = WordUtils.capitalize(structure.getName());

        Schema<?> queryWithTenantSelectionRef = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + "QueryWithTenantSelection");
        RequestBody querySelectionRequest = OpenApiUtils.createJsonRequest(queryWithTenantSelectionRef, "The Query and Tenant Selection to use for the operation.");

        Schema<?> tenantSpecificIdRef = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + "TenantSpecificId");

        // Create a path item for all the operations with basePath/structureApplication/structureName/:tenantId/:id"
        PathItem byTenantAndIdPathItem = new PathItem();

        // Operation for get by id
        Operation getByIdOperation = createOperation("Admin Get "+structureName+" by Id and Tenant",
                                                     "Gets " + structureName + " entities by their id and tenant.",
                                                     "get"+structureName+"ByIdAdmin",
                                                     structure,
                                                     1)
                .addParametersItem(OpenApiUtils.createPathParameter("tenantId", "The tenantId of the "+structureName+" to get."))
                .addParametersItem(OpenApiUtils.createPathParameter("id","The id of the "+structureName+" to get."));

        byTenantAndIdPathItem.get(getByIdOperation);

        // Operation for delete
        Operation deleteOperation = createOperation("Admin Delete "+structureName + " by Id and Tenant",
                                                    "Deletes " + structureName + " entities",
                                                    "delete"+structureName+"Admin",
                                                    structure,
                                                    -1)
                .addParametersItem(OpenApiUtils.createPathParameter("tenantId", "The tenantId of the "+structureName+" to delete."))
                .addParametersItem(OpenApiUtils.createPathParameter("id","The id of the "+structureName+" to delete."));

        byTenantAndIdPathItem.delete(deleteOperation);

        // add the path item to the paths
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/{tenantId}/{id}", byTenantAndIdPathItem);


        // Operation for delete by query
        PathItem deleteByQueryPathItem = new PathItem();
        Operation deleteByQueryOperation = createOperation("Admin Delete "+structureName+" by query",
                                                           "Delete " + structureName + " entities by query and tenant selection list",
                                                           "delete"+structureName+"ByQueryAdmin",
                                                           structure,
                                                           -1)
                .requestBody(querySelectionRequest);

        deleteByQueryPathItem.post(deleteByQueryOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/delete/by-query", deleteByQueryPathItem);

        // Find by Ids Operation
        PathItem findByIdsPathItem = new PathItem();
        Operation findByIdsOperation = createOperation("Admin Find "+structureName +" entities by ids",
                                                       "Find " + structureName + " entities by their ids.",
                                                       "find"+structureName+"ByIdsAdmin",
                                                       structure,
                                                       3)
                .requestBody(OpenApiUtils.createArrayRequest(tenantSpecificIdRef,"The array of TenantSpecificId's"));

        findByIdsPathItem.post(findByIdsOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/find/by-ids", findByIdsPathItem);

        // Create a path item for all the operations with basePath/structureApplication/structureName/
        // This will not conflict with save since the admin base prefix
        PathItem adminFindAllPathItem = new PathItem();

        // Find All Operation
        Operation findAllOperation = createOperation("Admin Find all "+structureName +" entities",
                                                     "Finds all " + structureName + " entities for the given tenants. Supports paging and sorting.",
                                                     "findAll"+structureName+"Admin",
                                                     structure,
                                                     2)
                .requestBody(OpenApiUtils.createStringArrayRequest("The list of tenants to find all entities for"));

        OpenApiUtils.addPagingAndSortingParameters(findAllOperation);

        adminFindAllPathItem.post(findAllOperation);

        // add the path item for all paths like basePath/structureApplication/structureName/
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName, adminFindAllPathItem);


        // total count Operation
        PathItem countPathItem = new PathItem();
        Operation countOperation = createOperation("Admin Get count for "+structureName,
                                                   "Gets total count of " + structureName + " entities for the given tenants.",
                                                   "count"+structureName+"Admin",
                                                   structure,
                                                   0)
                .requestBody(OpenApiUtils.createStringArrayRequest("The list of tenants to count all entities for"));
        countPathItem.post(countOperation);

        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/count/all", countPathItem);


        // total count for query Operation
        PathItem countByQueryPathItem = new PathItem();
        Operation countByQueryOperation = createOperation("Admin Get count by query for "+structureName,
                                                          "Gets total count of "+structureName+" entities by query and tenant selection list",
                                                          "count"+structureName+"ByQueryAdmin",
                                                          structure,
                                                          0)
                .requestBody(querySelectionRequest);

        countByQueryPathItem.post(countByQueryOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/count/by-query", countByQueryPathItem);


        // Create a path item for all the operations with basePath/structureApplication/structureName/search
        PathItem searchPathItem = new PathItem();
        Operation searchOperation = createOperation("Admin Search for "+structureName +" entities",
                                                    "Searches for " + structureName + " entities matching the search query and tenant selection list. Supports paging and sorting.",
                                                    "search"+structureName+"Admin",
                                                    structure,
                                                    2)
                .requestBody(querySelectionRequest);

        OpenApiUtils.addPagingAndSortingParameters(searchOperation);

        searchPathItem.post(searchOperation);

        // add the path item for search to the paths
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/search", searchPathItem);
    }


    @WithSpan
    private void addDefaultPathItems(Paths paths, Structure structure){

        String basePath = structuresProperties.getOpenApiPath();
        String lowercaseApplication = structure.getApplicationId().toLowerCase();
        String lowercaseName = structure.getName().toLowerCase();
        String structureName = WordUtils.capitalize(structure.getName());

        // Create a path item for all the operations with basePath/structureApplication/structureName/:id"
        PathItem byIdPathItem = new PathItem();

        // Operation for get by id
        Operation getByIdOperation = createOperation("Get "+structureName+" by Id",
                                                     "Gets " + structureName + " entities by their id.",
                                                     "get"+structureName+"ById",
                                                     structure,
                                                     1)
                .addParametersItem(OpenApiUtils.createPathParameter("id", "The id of the "+structureName+" to get."));
        byIdPathItem.get(getByIdOperation);

        // Operation for delete
        Operation deleteOperation = createOperation("Delete "+structureName,
                                                    "Deletes " + structureName + " entities",
                                                    "delete"+structureName,
                                                    structure,
                                                    -1)
                .addParametersItem(OpenApiUtils.createPathParameter("id", "The id of the "+structureName+" to delete."));
        byIdPathItem.delete(deleteOperation);

        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/{id}", byIdPathItem);


        // Operation for delete by query
        PathItem deleteByQueryPathItem = new PathItem();
        Operation deleteByQueryOperation = createOperation("Delete "+structureName+" by query",
                                                           "Delete " + structureName + " entities by query",
                                                           "delete"+structureName+"ByQuery",
                                                           structure,
                                                           -1)
                .requestBody(OpenApiUtils.createTextRequest("The query filter for delete operation"));
        deleteByQueryPathItem.post(deleteByQueryOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/delete/by-query", deleteByQueryPathItem);


        // Find by Ids Operation
        PathItem findByIdsPathItem = new PathItem();
        Operation findByIdsOperation = createOperation("Find "+structureName +" entities by ids",
                                                       "Find " + structureName + " entities by their ids.",
                                                       "find"+structureName+"ByIds",
                                                       structure,
                                                       3)
                .requestBody(OpenApiUtils.createStringArrayRequest("The array of id's"));
        findByIdsPathItem.post(findByIdsOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/find/by-ids", findByIdsPathItem);


        // Create a path item for all the operations with basePath/structureApplication/structureName/
        PathItem structurePathItem = new PathItem();

        // Request body for save operations
        Schema<?> structureRefSchema = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName());
        RequestBody structureRequestBody = OpenApiUtils.createJsonRequest(structureRefSchema, "The "+structureName+" to save or update");

        // Find All Operation
        Operation getAllOperation = createOperation("Find all "+structureName +" entities",
                                                    "Finds all " + structureName + " entities. Supports paging and sorting.",
                                                    "findAll"+structureName,
                                                    structure,
                                                    2);
        OpenApiUtils.addPagingAndSortingParameters(getAllOperation);
        structurePathItem.get(getAllOperation);


        // Save Operation
        Operation saveOperation = createOperation("Save "+structureName,
                                                  "Saves " + structureName + " entities.",
                                                  "save"+structureName,
                                                  structure,
                                                  1)
                .requestBody(structureRequestBody);
        structurePathItem.post(saveOperation);

        // add the path item for all paths like basePath/structureApplication/structureName/
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName, structurePathItem);

        // Sync Index operation
        PathItem syncPathItem = new PathItem();
        Operation syncOperation = createOperation("Sync " + structureName,
                                                  "Makes recent updates immediately available for search.",
                                                  "sync"+structureName,
                                                  structure,
                                                  -1);
        syncPathItem.get(syncOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/util/sync", syncPathItem);


        // Update Operation
        PathItem updatePathItem = new PathItem();
        Operation updateOperation = createOperation("Update "+structureName,
                                                    "Updates " + structureName + " entities.",
                                                    "update"+structureName,
                                                    structure,
                                                    1)
                .requestBody(structureRequestBody);
        updatePathItem.post(updateOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/update", updatePathItem);


        // Bulk Save Operation
        PathItem bulkSavePathItem = new PathItem();
        Operation bulkSaveOperation = createOperation("Bulk Save for "+structureName + " entities",
                                                      "Saves multiple " + structureName + " entities.",
                                                      "bulkSave"+structureName,
                                                      structure,
                                                      -1)
                .requestBody(OpenApiUtils.createArrayRequest(structureRefSchema, "List of entities to save"));
        bulkSavePathItem.post(bulkSaveOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/bulk", bulkSavePathItem);


        // Bulk Update Operation
        PathItem bulkUpdatePathItem = new PathItem();
        Operation bulkUpdateOperation = createOperation("Bulk Update for "+structureName + " entities",
                                                        "Updates multiple " + structureName + " entities.",
                                                        "bulkUpdate"+structureName,
                                                        structure,
                                                        -1)
                .requestBody(OpenApiUtils.createArrayRequest(structureRefSchema, "List of entities to update"));
        bulkUpdatePathItem.post(bulkUpdateOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/bulk-update", bulkUpdatePathItem);


        // total count Operation
        PathItem countPathItem = new PathItem();
        Operation countOperation = createOperation("Get count for "+structureName,
                                                   "Gets total count of " + structureName + " entities.",
                                                   "count"+structureName,
                                                   structure,
                                                   0);
        countPathItem.get(countOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/count/all", countPathItem);


        // total count for query Operation
        PathItem countByQueryPathItem = new PathItem();
        Operation countByQueryOperation = createOperation("Get count by query for "+structureName,
                                                          "Gets total count of "+structureName+" entities by query",
                                                          "count"+structureName+"ByQuery",
                                                          structure,
                                                          0)
                .requestBody(OpenApiUtils.createTextRequest("The query to get counts for"));
        countByQueryPathItem.post(countByQueryOperation);
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/count/by-query", countByQueryPathItem);


        // Create a path item for all the operations with basePath/structureApplication/structureName/search
        PathItem searchPathItem = new PathItem();
        Operation searchOperation = createOperation("Search for "+structureName +" entities",
                                                    "Searches for " + structureName + " entities matching the search criteria. Supports paging and sorting.",
                                                    "search"+structureName,
                                                    structure,
                                                    2)
                .requestBody(OpenApiUtils.createTextRequest("The search criteria"));

        OpenApiUtils.addPagingAndSortingParameters(searchOperation);

        searchPathItem.post(searchOperation);

        // add the path item for search to the paths
        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/search", searchPathItem);
    }

    @WithSpan
    private void addNamedQueryPathItems(Paths paths,
                                        String basePath,
                                        Structure structure,
                                        IdlConverter<Schema<?>, OpenApiConversionState> converter,
                                        Components components){

        String lowercaseApplication = structure.getApplicationId().toLowerCase();
        String lowercaseName = structure.getName().toLowerCase();
        NamedQueriesDefinition namedQueriesDefinition = namedQueriesService.findByApplicationAndStructure(structure.getApplicationId(),
                                                                                                        structure.getName())
                                                                           .join();
        if(namedQueriesDefinition != null){

            // For any FunctionDefinition create a Named Query path item
            for(FunctionDefinition query : namedQueriesDefinition.getNamedQueries()){
                String queryName = query.getName();
                String summary = "Named query: " + queryName;
                QueryDecorator queryDecorator = query.findDecorator(QueryDecorator.class);
                if(queryDecorator != null) {

                    // Build the response schema
                    ApiResponse response = createResponse(queryName,
                                                          query,
                                                          converter);

                    Operation queryOperation = createOperation(summary,
                                                               "Executes the named query " + queryName,
                                                               queryName,
                                                               structure,
                                                               response);

                    // Build the request body if there are parameters
                    RequestBody requestBody = convertParamsToRequestBody(queryName,
                                                                         query.getParameters(),
                                                                         converter,
                                                                         components);
                    if (requestBody != null) {
                        queryOperation.requestBody(requestBody);
                    }

                    PathItem queryPathItem = new PathItem();
                    queryPathItem.post(queryOperation);

                    // vertx route is
                    // :structureApplication/:structureName/named-query/:queryName
                    // TODO: should we also check if a Pageable parameter is defined in the FunctionDefinition
                    if (query.getReturnType() instanceof PageC3Type) {
                        SqlQueryType queryType = QueryUtils.determineQueryType(queryDecorator.getStatements());
                        switch (queryType) {
                            case AGGREGATE:
                                OpenApiUtils.addCursorPagingWithoutSortParameters(queryOperation);
                                break;
                            case SELECT:
                                OpenApiUtils.addPagingAndSortingParameters(queryOperation);
                                break;
                            default:
                                log.warn("Named query {} in Structure {} has a Page return type but, paging is not supported for query type {}. No page parameters will be added.",
                                         queryName,
                                         structure.getName(),
                                         queryType);
                                break;
                        }
                        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/named-query-page/" + queryName,
                                  queryPathItem);
                    } else {
                        paths.put(basePath + lowercaseApplication + "/" + lowercaseName + "/named-query/" + queryName,
                                  queryPathItem);
                    }
                }else{
                    log.warn("No QueryDecorator found for Named query {} in Structure {}. No OpenAPI path will be created.", queryName, structure.getName());
                }
            }
        }
    }

    private static ApiResponse createResponse(String queryName,
                                              FunctionDefinition query,
                                              IdlConverter<Schema<?>, OpenApiConversionState> converter) {

        ApiResponse response = new ApiResponse().description(queryName + " OK");
        Content content = new Content();

        C3Type returnType = query.getReturnType();

        if(!(returnType instanceof VoidC3Type)){
            MediaType mediaType = new MediaType();
            mediaType.setSchema(converter.convert(returnType));
            content.addMediaType("application/json", mediaType);
        }

        response.setContent(content);
        return response;
    }

    private RequestBody convertParamsToRequestBody(String name,
                                                   List<ParameterDefinition> parameters,
                                                   IdlConverter<Schema<?>, OpenApiConversionState> converter,
                                                   Components components){

        String requestSchemaName = WordUtils.capitalize(name) + "Request";
        ObjectSchema requestSchema = new ObjectSchema();
        requestSchema.setName(requestSchemaName);

        for(ParameterDefinition parameter : parameters){

            // We skip the pageable since this will be provided as a http query parameter
            if(!(parameter.getType() instanceof PageableC3Type)){

                Schema<?> schema = converter.convert(parameter.getType());

                // if this is an object we create a reference schema
                if(parameter.getType() instanceof ComplexC3Type complexField){
                    components.addSchemas(complexField.getName(), schema);
                    schema = new Schema<>().$ref("#/components/schemas/"+complexField.getName());
                }
                requestSchema.addProperty(parameter.getName(), schema);
            }
        }

        // If no properties then we return null
        if(requestSchema.getProperties() != null
                && !requestSchema.getProperties().isEmpty()) {

            components.addSchemas(requestSchemaName, requestSchema);
            Schema<?> refSchema = new Schema<>().$ref("#/components/schemas/" + requestSchemaName);

            return new RequestBody().description("The Body for the named query " + name)
                                    .content(new Content()
                                                     .addMediaType("application/json",
                                                                   new MediaType().schema(refSchema)));
        } else {
            return null;
        }
    }

    private Operation createOperation(String operationSummary,
                                      String description,
                                      String operationId,
                                      Structure structure,
                                      int responseType) {

        // create a response for the structure item
        ApiResponse response = new ApiResponse().description(operationSummary + " OK");
        Content content = new Content();
        MediaType mediaType = new MediaType();

        if(responseType == 0){ // Count Response

            mediaType.setSchema(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + "CountResponse"));

        }else if(responseType == 1){ // Structure Response

            mediaType.setSchema(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName()));

        }else if(responseType == 2){ // Page Response

            ObjectSchema pageSchema = OpenApiUtils.createPageSchema(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName()));
            mediaType.setSchema(pageSchema);

        }else if(responseType == 3){ // Array Response

            mediaType.setSchema(new ArraySchema()
                                        .items(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName())));

        }

        content.addMediaType("application/json", mediaType);
        response.setContent(content);

        return createOperation(operationSummary,
                               description,
                               operationId,
                               structure,
                               response);
    }

    private Operation createOperation(String operationSummary,
                                      String description,
                                      String operationId,
                                      Structure structure,
                                      ApiResponse response) {

        Operation operation = new Operation().summary(operationSummary)
                                             .description(description)
                                             .tags(List.of(structure.getName()))
                                             .operationId(operationId);

        if (structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BASIC) {
            operation.security(List.of(new SecurityRequirement().addList("BasicAuth")));
        } else if (structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BEARER) {
            operation.security(List.of(new SecurityRequirement().addList("BearerAuth")));
        }

        // Add the default responses and the response for the structure item being returned
        ApiResponses defaultResponses = getDefaultResponses();

        defaultResponses.put("200", response);

        operation.setResponses(defaultResponses);

        return operation;
    }

}
