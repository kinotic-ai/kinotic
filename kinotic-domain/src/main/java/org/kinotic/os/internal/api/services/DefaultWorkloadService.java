package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.workload.Workload;
import org.kinotic.os.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultWorkloadService extends AbstractCrudService<Workload> implements WorkloadService {

    public DefaultWorkloadService(ElasticsearchAsyncClient esAsyncClient,
                                  CrudServiceTemplate crudServiceTemplate,
                                  SecurityContext participantContext) {
        super("kinotic_workload",
              Workload.class,
              esAsyncClient,
              crudServiceTemplate,
              participantContext);
    }

    @Override
    public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder
                .query(q -> q
                        .bool(b -> b
                                .filter(TermQuery.of(tq -> tq.field("nodeId").value(nodeId))._toQuery())
                        )));
    }

    @Override
    public CompletableFuture<Long> countForNode(String nodeId) {
        return crudServiceTemplate.count(indexName, builder -> builder
                .query(q -> q
                        .bool(b -> b
                                .filter(TermQuery.of(tq -> tq.field("nodeId").value(nodeId))._toQuery())
                        )));
    }

    @Override
    public CompletableFuture<Workload> save(Workload entity) {
        Validate.notNull(entity, "Workload cannot be null");
        Validate.notNull(entity.getName(), "Workload name cannot be null");
        Validate.notNull(entity.getImage(), "Workload image cannot be null");

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        if (entity.getCreated() == null) {
            entity.setCreated(new Date());
        }
        return super.save(entity);
    }

}
