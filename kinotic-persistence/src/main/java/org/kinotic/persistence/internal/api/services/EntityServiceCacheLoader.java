package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.hooks.DecoratorLogic;
import org.kinotic.persistence.internal.api.hooks.DelegatingUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.api.model.DecoratedProperty;
import org.kinotic.persistence.internal.utils.PersistenceUtil;
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
public class EntityServiceCacheLoader implements AsyncCacheLoader<String, EntityRepository> {

    private final AuthorizationServiceFactory authServiceFactory;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final NamedQueriesService namedQueriesService;
    private final JsonMapper jsonMapper;
    private final ReadPreProcessor readPreProcessor;
    private final EntityDefinitionDAO entityDefinitionDAO;
    private final SecurityContext securityContext;
    private final PersistenceProperties persistenceProperties;
    private final Map<String, UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors;


    public EntityServiceCacheLoader(AuthorizationServiceFactory authServiceFactory,
                                    CrudServiceTemplate crudServiceTemplate,
                                    ElasticsearchAsyncClient esAsyncClient,
                                    NamedQueriesService namedQueriesService,
                                    JsonMapper jsonMapper,
                                    ReadPreProcessor readPreProcessor,
                                    EntityDefinitionDAO entityDefinitionDAO,
                                    SecurityContext securityContext,
                                    PersistenceProperties persistenceProperties,
                                    List<UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors) {
        this.authServiceFactory = authServiceFactory;
        this.crudServiceTemplate = crudServiceTemplate;
        this.esAsyncClient = esAsyncClient;
        this.namedQueriesService = namedQueriesService;
        this.jsonMapper = jsonMapper;
        this.readPreProcessor = readPreProcessor;
        this.entityDefinitionDAO = entityDefinitionDAO;
        this.securityContext = securityContext;
        this.persistenceProperties = persistenceProperties;

        this.upsertFieldPreProcessors = PersistenceUtil.listToMap(upsertFieldPreProcessors,
                                                                 p -> p.implementsDecorator().getName());
    }


    @Override
    public CompletableFuture<? extends EntityRepository> asyncLoad(String key, Executor executor) throws Exception {
        return securityContext.withElevatedAccess(() -> entityDefinitionDAO.findById(key))
                                  .thenApply(entityDefinition -> {
                               Validate.notNull(entityDefinition, "No EntityDefinition found for key: " + key);
                               return entityDefinition;
                           })
                                  .thenComposeAsync(this::createEntityService, executor);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<EntityRepository> createEntityService(EntityDefinition entityDefinition) {

        if(entityDefinition == null){
            return CompletableFuture.failedFuture(new IllegalArgumentException("EntityDefinition must not be null"));
        } else if (!entityDefinition.isPublished()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("EntityDefinition must be published"));
        }

        // Map of jsonPath to DecoratorLogic
        Map<String, DecoratorLogic> fieldPreProcessors = new LinkedHashMap<>();

        for(DecoratedProperty decoratedProperty : entityDefinition.getDecoratedProperties()){

            for(C3Decorator decorator : decoratedProperty.getDecorators()){

                UpsertFieldPreProcessor<C3Decorator, Object, Object> processor =
                        (UpsertFieldPreProcessor<C3Decorator, Object, Object>)upsertFieldPreProcessors.get(decorator.getClass().getName());
                if(processor != null){
                    fieldPreProcessors.put(decoratedProperty.getJsonPath(), new DecoratorLogic(decorator, processor));
                }
            }
        }

        return authServiceFactory.createEntityDefinitionAuthorizationService(entityDefinition)
                                 .thenApply(authService -> new DefaultEntityRepository(
                                         authService,
                                         crudServiceTemplate,
                                         new DelegatingUpsertPreProcessor(persistenceProperties,
                                                                          jsonMapper,
                                                                          entityDefinition,
                                                                          fieldPreProcessors),
                                         esAsyncClient,
                                         namedQueriesService,
                                         jsonMapper,
                                         readPreProcessor,
                                         entityDefinition,
                                         persistenceProperties));
    }

}
