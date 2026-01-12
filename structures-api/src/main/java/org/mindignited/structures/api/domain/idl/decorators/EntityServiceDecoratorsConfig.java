package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Locked;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.mindignited.structures.api.domain.EntityOperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the {@link EntityServiceDecoratorsDecorator}
 * This maps EntityService method names to decorators that should be applied to the method
 */
@Getter
@Setter
@Accessors(chain = true)
public class EntityServiceDecoratorsConfig{

    /**
     * The following map crud operations to decorators
     * These will be applied to the corresponding method type in the EntityService
     * i.e. create will be applied to the save method, read will be applied to count, findAll, findById, ect
     */
    private List<EntityServiceDecorator> allCreate = List.of();
    private List<EntityServiceDecorator> allRead = List.of();
    private List<EntityServiceDecorator> allUpdate = List.of();
    private List<EntityServiceDecorator> allDelete = List.of();

    /**
     * The following map individual methods to decorators
     * These will be applied to the corresponding method in the EntityService
     * i.e. bulkSave will be applied to the bulkSave method
     */
    private List<EntityServiceDecorator> bulkSave = List.of();
    private List<EntityServiceDecorator> bulkUpdate = List.of();
    private List<EntityServiceDecorator> count = List.of();
    private List<EntityServiceDecorator> countByQuery = List.of();
    private List<EntityServiceDecorator> deleteById = List.of();
    private List<EntityServiceDecorator> deleteByQuery = List.of();
    private List<EntityServiceDecorator> findAll = List.of();
    private List<EntityServiceDecorator> findById = List.of();
    private List<EntityServiceDecorator> findByIds = List.of();
    private List<EntityServiceDecorator> syncIndex = List.of();
    private List<EntityServiceDecorator> save = List.of();
    private List<EntityServiceDecorator> search = List.of();
    private List<EntityServiceDecorator> update = List.of();

    @JsonIgnore
    private Map<EntityOperation, List<EntityServiceDecorator>> cachedOperationDecoratorMap;

    @JsonIgnore
    @Locked
    public Map<EntityOperation, List<EntityServiceDecorator>> getOperationDecoratorMap() {
        if (cachedOperationDecoratorMap != null) {
            return cachedOperationDecoratorMap;
        }

        cachedOperationDecoratorMap = new HashMap<>();

        // Populate specific operation map
        Map<EntityOperation, List<EntityServiceDecorator>> specificOperationMap = new HashMap<>();
        specificOperationMap.put(EntityOperation.BULK_SAVE, bulkSave);
        specificOperationMap.put(EntityOperation.BULK_UPDATE, bulkUpdate);
        specificOperationMap.put(EntityOperation.COUNT, count);
        specificOperationMap.put(EntityOperation.COUNT_BY_QUERY, countByQuery);
        specificOperationMap.put(EntityOperation.DELETE_BY_ID, deleteById);
        specificOperationMap.put(EntityOperation.DELETE_BY_QUERY, deleteByQuery);
        specificOperationMap.put(EntityOperation.FIND_ALL, findAll);
        specificOperationMap.put(EntityOperation.FIND_BY_ID, findById);
        specificOperationMap.put(EntityOperation.FIND_BY_IDS, findByIds);
        specificOperationMap.put(EntityOperation.SYNC_INDEX, syncIndex);
        specificOperationMap.put(EntityOperation.SAVE, save);
        specificOperationMap.put(EntityOperation.SEARCH, search);
        specificOperationMap.put(EntityOperation.UPDATE, update);

        // Populate CRUD operation map in alphabetical order of operations
        Map<EntityOperation, List<EntityServiceDecorator>> crudOperationMap = new HashMap<>();
        crudOperationMap.put(EntityOperation.BULK_SAVE, allCreate);
        crudOperationMap.put(EntityOperation.BULK_UPDATE, allUpdate);
        crudOperationMap.put(EntityOperation.COUNT, allRead);
        crudOperationMap.put(EntityOperation.COUNT_BY_QUERY, allRead);
        crudOperationMap.put(EntityOperation.DELETE_BY_ID, allDelete);
        crudOperationMap.put(EntityOperation.DELETE_BY_QUERY, allDelete);
        crudOperationMap.put(EntityOperation.FIND_ALL, allRead);
        crudOperationMap.put(EntityOperation.FIND_BY_ID, allRead);
        crudOperationMap.put(EntityOperation.FIND_BY_IDS, allRead);
        crudOperationMap.put(EntityOperation.SYNC_INDEX, allRead);
        crudOperationMap.put(EntityOperation.SAVE, allCreate);
        crudOperationMap.put(EntityOperation.SEARCH, allRead);
        crudOperationMap.put(EntityOperation.UPDATE, allUpdate);

        // Combine specific and CRUD lists
        for (EntityOperation operation : EntityOperation.values()) {
            List<EntityServiceDecorator> specificDecorators = specificOperationMap.getOrDefault(operation, List.of());
            List<EntityServiceDecorator> decorators;

            if (!specificDecorators.isEmpty()) {
                decorators = specificDecorators;
            } else {
                decorators = crudOperationMap.getOrDefault(operation, List.of());
            }

            cachedOperationDecoratorMap.put(operation, decorators);
        }

        return cachedOperationDecoratorMap;
    }

}
