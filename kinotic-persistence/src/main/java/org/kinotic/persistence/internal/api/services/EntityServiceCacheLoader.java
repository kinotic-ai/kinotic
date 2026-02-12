package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import org.apache.commons.lang3.Validate;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.hooks.DecoratorLogic;
import org.kinotic.persistence.internal.api.hooks.DelegatingUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.api.domain.DecoratedProperty;
import org.kinotic.persistence.internal.utils.StructuresUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * This simplifies the creation of the EntityService, since a lot of dependencies are needed.
 * Created by Navíd Mitchell 🤪 on 5/10/23.
 */
@Component
public class EntityServiceCacheLoader implements AsyncCacheLoader<String, EntityService> {

    private final AuthorizationServiceFactory authServiceFactory;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final NamedQueriesService namedQueriesService;
    private final JsonMapper jsonMapper;
    private final ReadPreProcessor readPreProcessor;
    private final StructureDAO structureDAO;
    private final StructuresProperties structuresProperties;
    private final Map<String, UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors;


    public EntityServiceCacheLoader(AuthorizationServiceFactory authServiceFactory,
                                    CrudServiceTemplate crudServiceTemplate,
                                    ElasticsearchAsyncClient esAsyncClient,
                                    NamedQueriesService namedQueriesService,
                                    JsonMapper jsonMapper,
                                    ReadPreProcessor readPreProcessor,
                                    StructureDAO structureDAO,
                                    StructuresProperties structuresProperties,
                                    List<UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors) {
        this.authServiceFactory = authServiceFactory;
        this.crudServiceTemplate = crudServiceTemplate;
        this.esAsyncClient = esAsyncClient;
        this.namedQueriesService = namedQueriesService;
        this.jsonMapper = jsonMapper;
        this.readPreProcessor = readPreProcessor;
        this.structureDAO = structureDAO;
        this.structuresProperties = structuresProperties;

        this.upsertFieldPreProcessors = StructuresUtil.listToMap(upsertFieldPreProcessors,
                                                                 p -> p.implementsDecorator().getName());
    }


    @Override
    public CompletableFuture<? extends EntityService> asyncLoad(String key, Executor executor) throws Exception {
        return structureDAO.findById(key)
                           .thenApply(structure -> {
                               Validate.notNull(structure, "No Structure found for key: " + key);
                               return structure;
                           })
                           .thenComposeAsync(this::createEntityService, executor);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<EntityService> createEntityService(Structure structure) {

        if(structure == null){
            return CompletableFuture.failedFuture(new IllegalArgumentException("Structure must not be null"));
        } else if (!structure.isPublished()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Structure must be published"));
        }

        // Map of jsonPath to DecoratorLogic
        Map<String, DecoratorLogic> fieldPreProcessors = new LinkedHashMap<>();

        for(DecoratedProperty decoratedProperty : structure.getDecoratedProperties()){

            for(C3Decorator decorator : decoratedProperty.getDecorators()){

                UpsertFieldPreProcessor<C3Decorator, Object, Object> processor =
                        (UpsertFieldPreProcessor<C3Decorator, Object, Object>)upsertFieldPreProcessors.get(decorator.getClass().getName());
                if(processor != null){
                    fieldPreProcessors.put(decoratedProperty.getJsonPath(), new DecoratorLogic(decorator, processor));
                }
            }
        }

        return authServiceFactory.createStructureAuthorizationService(structure)
                                 .thenApply(authService -> new DefaultEntityService(
                                         authService,
                                         crudServiceTemplate,
                                         new DelegatingUpsertPreProcessor(structuresProperties,
                                                                          jsonMapper,
                                                                          structure,
                                                                          fieldPreProcessors),
                                         esAsyncClient,
                                         namedQueriesService,
                                         jsonMapper,
                                         readPreProcessor,
                                         structure,
                                         structuresProperties));
    }

}
