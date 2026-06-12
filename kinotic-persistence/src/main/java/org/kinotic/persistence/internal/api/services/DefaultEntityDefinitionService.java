package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.DataStreamVisibility;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractProjectScopedService;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.api.repositories.EntityDefinitionRepository;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.utils.PersistenceUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Component
public class DefaultEntityDefinitionService extends AbstractProjectScopedService<EntityDefinition>
        implements EntityDefinitionService {

    private final ApplicationEventPublisher eventPublisher;
    private final CrudServiceTemplate crudServiceTemplate;
    private final EntityDefinitionConversionService entityDefinitionConversionService;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final PersistenceProperties persistenceProperties;

    public DefaultEntityDefinitionService(ApplicationEventPublisher eventPublisher,
                                          CrudServiceTemplate crudServiceTemplate,
                                          EntityDefinitionConversionService entityDefinitionConversionService,
                                          EntityDefinitionRepository entityDefinitionRepository,
                                          PersistenceProperties persistenceProperties,
                                          SecurityContext securityContext) {
        super(entityDefinitionRepository, securityContext);
        this.eventPublisher = eventPublisher;
        this.crudServiceTemplate = crudServiceTemplate;
        this.entityDefinitionConversionService = entityDefinitionConversionService;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.persistenceProperties = persistenceProperties;
    }

    @WithSpan
    @Override
    public CompletableFuture<EntityDefinition> create(@SpanAttribute("entityDefinition") EntityDefinition entityDefinition) {
        try {
            // will throw an exception if invalid
            PersistenceUtil.validateEntityDefinition(entityDefinition);

            entityDefinition.setApplicationId(entityDefinition.getApplicationId().trim());
            entityDefinition.setProjectId(entityDefinition.getProjectId().trim());
            entityDefinition.setName(entityDefinition.getName().trim());
            String logicalIndexName = PersistenceUtil.createEntityDefinitionId(entityDefinition.getOrganizationId(),
                                                                               entityDefinition.getApplicationId(),
                                                                               entityDefinition.getName());

            if(logicalIndexName.length() > 255){
                throw new IllegalArgumentException("EntityDefinition Id is too long, 'applicationId.name' must be less than 256 characters");
            }

            // TODO: how to ensure EntityDefinition application name match the C3Type name
            // Should we just use the EntityDefinition one?

            entityDefinition.setId(logicalIndexName);
            entityDefinition.setCreated(new Date());
            entityDefinition.setUpdated(entityDefinition.getCreated());
            // Store name of the elastic search index for items
            entityDefinition.setItemIndex(this.persistenceProperties.getIndexPrefix() + logicalIndexName);

            ElasticConversionResult result = entityDefinitionConversionService.convertToElasticMapping(entityDefinition);

            entityDefinition.setDecoratedProperties(result.decoratedProperties());
            entityDefinition.setMultiTenancyType(result.entityDecorator().getMultiTenancyType());
            entityDefinition.setEntityType(result.entityDecorator().getEntityType());
            entityDefinition.setVersionFieldName(result.versionFieldName());
            entityDefinition.setTenantIdFieldName(result.tenantIdFieldName());
            entityDefinition.setTimeReferenceFieldName(result.timeReferenceFieldName());

        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        // super.create uses the ES create op-type, so the uniqueness check is atomic at the
        // shard — a concurrent duplicate fails instead of overwriting the way the previous
        // findById-then-save did.
        return super.create(entityDefinition)
                .exceptionallyCompose(ex -> AlreadyExistsException.isCause(ex)
                        ? CompletableFuture.failedFuture(new IllegalArgumentException(
                                "EntityDefinition Application+Name must be unique, '" + entityDefinition.getId() + "' already exists."))
                        : CompletableFuture.failedFuture(ex));
    }

    @WithSpan
    @Override
    protected CompletableFuture<Void> beforeDelete(@SpanAttribute("entityDefinitionId") String entityDefinitionId) {
        return findById(entityDefinitionId)
                .thenCompose(entityDefinition -> {

                    if(entityDefinition == null){
                        return CompletableFuture.failedFuture(new IllegalArgumentException("EntityDefinition cannot be found for id: " + entityDefinitionId));
                    }

                    if(entityDefinition.isPublished()){
                        return CompletableFuture
                                .failedFuture(new IllegalStateException("EntityDefinition must be Un-Published before Deleting"));
                    }

                    this.eventPublisher.publishEvent(CacheEvictionEvent.localDeletedEntityDefinition(entityDefinition.getOrganizationId(), entityDefinition.getApplicationId(), entityDefinition.getId()));

                    return CompletableFuture.completedFuture(null);
                });
    }

    @WithSpan
    @Override
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(@SpanAttribute("applicationId") String applicationId, Pageable pageable) {
        return entityDefinitionRepository.findAllPublishedForApplication(applicationId, requireOrganizationId(), pageable);
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> publish(@SpanAttribute("entityDefinitionId") String entityDefinitionId) {
        return findById(entityDefinitionId)
                .thenCompose(entityDefinition -> {
                    if (entityDefinition == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("EntityDefinition cannot be found for id: " + entityDefinitionId));
                    }
                    if (entityDefinition.isPublished()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("EntityDefinition is already published"));
                    }

                    ElasticConversionResult result = entityDefinitionConversionService.convertToElasticMapping(entityDefinition);
                    Map<String, Property> mappings = result.objectProperty().properties();
                    String templateName = entityDefinition.getItemIndex() + "_tpl";
                    boolean allowCustomRouting = entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED;

                    CompletableFuture<Void> creationFuture = entityDefinition.isStream()
                            ? crudServiceTemplate
                            .createIndexTemplate(templateName,
                                                 entityDefinition.getItemIndex() + "*",
                                                 DataStreamVisibility.of(b -> b.allowCustomRouting(allowCustomRouting)),
                                                 mappings)
                            .thenCompose(v -> crudServiceTemplate.createDataStream(entityDefinition.getItemIndex()))
                            : crudServiceTemplate
                            .createIndex(entityDefinition.getItemIndex(), true, mappings);

                    return creationFuture.thenCompose(v -> {
                        entityDefinition.setPublished(true);
                        entityDefinition.setPublishedTimestamp(new Date());
                        entityDefinition.setUpdated(entityDefinition.getPublishedTimestamp());
                        return super.save(entityDefinition)
                                .thenApply(entityDefinition1 -> {
                                    this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedEntityDefinition(entityDefinition1.getOrganizationId(), entityDefinition1.getApplicationId(),
                                                                                                                     entityDefinition1.getId()));
                                    return null;
                                });
                    });
                });
    }

    @WithSpan
    @Override
    public CompletableFuture<EntityDefinition> save(@SpanAttribute("entityDefinition") EntityDefinition entityDefinition) {
        try {
            Validate.notBlank(entityDefinition.getId(), "EntityDefinition Id Invalid");
            PersistenceUtil.validateEntityDefinition(entityDefinition);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        return findById(entityDefinition.getId())
                .thenCompose(existingEntityDefinition -> {
                    if (existingEntityDefinition == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("EntityDefinition cannot be found for id: " + entityDefinition.getId()));
                    }

                    entityDefinition.setUpdated(new Date());
                    entityDefinition.setCreated(existingEntityDefinition.getCreated());
                    entityDefinition.setName(existingEntityDefinition.getName());
                    entityDefinition.setApplicationId(existingEntityDefinition.getApplicationId());
                    entityDefinition.setProjectId(existingEntityDefinition.getProjectId());
                    entityDefinition.setItemIndex(existingEntityDefinition.getItemIndex());
                    entityDefinition.setPublished(existingEntityDefinition.isPublished());
                    entityDefinition.setPublishedTimestamp(existingEntityDefinition.getPublishedTimestamp());

                    ElasticConversionResult result = entityDefinitionConversionService.convertToElasticMapping(entityDefinition);
                    Map<String, Property> mappings = result.objectProperty().properties();

                    entityDefinition.setDecoratedProperties(result.decoratedProperties());
                    entityDefinition.setMultiTenancyType(result.entityDecorator().getMultiTenancyType());
                    entityDefinition.setEntityType(result.entityDecorator().getEntityType());
                    entityDefinition.setVersionFieldName(result.versionFieldName());
                    entityDefinition.setTenantIdFieldName(result.tenantIdFieldName());
                    entityDefinition.setTimeReferenceFieldName(result.timeReferenceFieldName());

                    if (entityDefinition.isPublished()) {
                        if (!existingEntityDefinition.isMultiTenantSelectionEnabled()
                                && entityDefinition.isMultiTenantSelectionEnabled()
                                && !persistenceProperties.getTenantIdFieldName().equals(entityDefinition.getTenantIdFieldName())) {
                            return CompletableFuture.failedFuture(
                                    new IllegalArgumentException("When enabling multi-tenant selection for an existing published EntityDefinition, the tenantId field must be set to: " + persistenceProperties.getTenantIdFieldName()));
                        }

                        if (!existingEntityDefinition.isStream() && entityDefinition.isStream()) {
                            return CompletableFuture.failedFuture(
                                    new IllegalArgumentException("Cannot change an existing published EntityDefinition from a non-stream to a stream"));
                        }

                        // FIXME: how to best handle an operation where the mapping completes but the save fails.
                        //        Additionally this could have serious race conditions if multiple clients are updating the same EntityDefinition
                        //        This could probably be solved by verifying the mapping is still valid before saving
                        //        (diff the fields and make sure only fields are added and no types are changed)
                        //        Then this could be moved to save the EntityDefinition first with optimistic locking, and if that succeeds then update the mapping


                        CompletableFuture<Void> updateFuture;
                        if (entityDefinition.isStream()) {
                            String templateName = entityDefinition.getItemIndex() + "_tpl";
                            // Update both the template (for future indices) and the data stream's current indices
                            updateFuture = crudServiceTemplate.updateIndexTemplate(templateName, mappings)
                                                              .thenCompose(v -> crudServiceTemplate.updateIndexMapping(
                                                                      entityDefinition.getItemIndex(), mappings));
                        } else {
                            // For regular indices, just update the mappings
                            updateFuture = crudServiceTemplate.updateIndexMapping(entityDefinition.getItemIndex(), mappings);
                        }

                        return updateFuture.thenCompose(v -> super.save(entityDefinition)
                                .thenApply(entityDefinition1 -> {
                                    this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedEntityDefinition(entityDefinition1.getOrganizationId(), entityDefinition1.getApplicationId(), entityDefinition1.getId()));
                                    return entityDefinition1;
                                }));
                    } else {
                        return super.save(entityDefinition);
                    }
                });
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> unPublish(@SpanAttribute("entityDefinitionId") String entityDefinitionId) {
        return findById(entityDefinitionId)
                .thenCompose(entityDefinition -> {
                    if (entityDefinition == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("EntityDefinition cannot be found for id: " + entityDefinitionId));
                    }

                    if (!entityDefinition.isPublished()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("EntityDefinition is not published"));
                    }

                    CompletableFuture<Void> deleteStorageFuture;
                    if (entityDefinition.isStream()) {
                        String templateName = entityDefinition.getItemIndex() + "_tpl";
                        // Delete the data stream and its template
                        deleteStorageFuture = crudServiceTemplate.deleteDataStream(entityDefinition.getItemIndex())
                                                                 .thenCompose(v -> crudServiceTemplate.deleteIndexTemplate(templateName));
                    } else {
                        // Delete the regular index
                        deleteStorageFuture = crudServiceTemplate.deleteIndex(entityDefinition.getItemIndex());
                    }

                    return deleteStorageFuture.thenCompose(v -> {
                        entityDefinition.setPublished(false);
                        entityDefinition.setPublishedTimestamp(null);
                        entityDefinition.setUpdated(new Date());
                        return super.save(entityDefinition)
                                .thenApply(entityDefinition1 -> {
                                    this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedEntityDefinition(entityDefinition1.getOrganizationId(), entityDefinition1.getApplicationId(), entityDefinition1.getId()));
                                    return null;
                                });
                    });
                });
    }
}
