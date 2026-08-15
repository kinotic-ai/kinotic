package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
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
import org.kinotic.persistence.internal.api.repositories.EntityDefinitionRepository;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.utils.PersistenceUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Owns the cache of {@link EntityService}s, keyed by {@code (organizationId, entityDefinitionId)},
 * and builds one on a miss. Building an EntityService needs a lot of dependencies, which is why
 * this is its own component.
 * Created by Navíd Mitchell 🤪 on 5/10/23.
 */
@Component
public class EntityServiceCache {

    private final AuthorizationServiceFactory authServiceFactory;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final NamedQueriesService namedQueriesService;
    private final JsonMapper jsonMapper;
    private final ReadPreProcessor readPreProcessor;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final PersistenceProperties persistenceProperties;
    private final Map<String, UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors;
    private final AsyncLoadingCache<CacheKey, EntityService> cache;

    public EntityServiceCache(AuthorizationServiceFactory authServiceFactory,
                                    CrudServiceTemplate crudServiceTemplate,
                                    ElasticsearchAsyncClient esAsyncClient,
                                    NamedQueriesService namedQueriesService,
                                    JsonMapper jsonMapper,
                                    ReadPreProcessor readPreProcessor,
                                    EntityDefinitionRepository entityDefinitionRepository,
                                    PersistenceProperties persistenceProperties,
                                    List<UpsertFieldPreProcessor<?, ?, ?>> upsertFieldPreProcessors,
                                    DefaultCaffeineCacheFactory cacheFactory) {
        this.authServiceFactory = authServiceFactory;
        this.crudServiceTemplate = crudServiceTemplate;
        this.esAsyncClient = esAsyncClient;
        this.namedQueriesService = namedQueriesService;
        this.jsonMapper = jsonMapper;
        this.readPreProcessor = readPreProcessor;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.persistenceProperties = persistenceProperties;

        this.upsertFieldPreProcessors = PersistenceUtil.listToMap(upsertFieldPreProcessors,
                                                                 p -> p.implementsDecorator().getName());

        this.cache = cacheFactory.<CacheKey, EntityService>newBuilder()
                                 .name("entityServiceCache")
                                 .expireAfterAccess(Duration.ofHours(20))
                                 .maximumSize(2000)
                                 .buildAsync(this::load);
    }

    /**
     * Returns the {@link EntityService} for {@code entityDefinitionId} within {@code organizationId},
     * building and caching it on a miss.
     */
    public Future<EntityService> get(String organizationId, String entityDefinitionId) {
        return crudServiceTemplate.toFuture(cache.get(new CacheKey(organizationId, entityDefinitionId)));
    }

    /**
     * Evicts the cached {@link EntityService} for {@code entityDefinitionId} within {@code organizationId}.
     */
    public void evict(String organizationId, String entityDefinitionId) {
        cache.asMap().remove(new CacheKey(organizationId, entityDefinitionId));
    }

    // Caffeine's AsyncCacheLoader contract requires the (key, executor) signature and a CompletableFuture result
    private CompletableFuture<EntityService> load(CacheKey key, Executor executor) {
        return entityDefinitionRepository.findById(key.entityDefinitionId(), key.organizationId())
                .map(entityDefinition -> {
                    Validate.notNull(entityDefinition, "No EntityDefinition found for %s", key);
                    return entityDefinition;
                })
                .compose(this::createEntityService)
                .toCompletionStage()
                .toCompletableFuture();
    }

    @SuppressWarnings("unchecked")
    public Future<EntityService> createEntityService(EntityDefinition entityDefinition) {

        if(entityDefinition == null){
            return Future.failedFuture(new IllegalArgumentException("EntityDefinition must not be null"));
        } else if (!entityDefinition.isPublished()) {
            return Future.failedFuture(new IllegalArgumentException("EntityDefinition must be published"));
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
                                 .map(authService -> new DefaultEntityService(
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

    private record CacheKey(String organizationId, String entityDefinitionId) {}

}
