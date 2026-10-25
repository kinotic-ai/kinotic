package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.domain.api.model.ServerSbom;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Stores the {@link ServerSbom} of each server node, keyed by the node's Ignite id.
 */
@Component
public class ServerSbomRepository extends AbstractRepository<ServerSbom> {

    public ServerSbomRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_server_sbom", ServerSbom.class, crudServiceTemplate);
    }

    /**
     * Lists the SBOM of every node, each holding only its id.
     *
     * @param pageable the page to return; sorted by {@code id}
     */
    public Future<Page<ServerSbom>> findAllIds(CursorPageable pageable) {
        return findAll(pageable, b -> b.source(src -> src.filter(f -> f.includes("id"))));
    }

    /**
     * Deletes the SBOMs of the nodes with the ids.
     */
    public Future<Void> deleteAllById(Collection<String> ids) {
        Validate.notEmpty(ids, "ids cannot be empty");
        List<String> values = List.copyOf(ids);
        return crudServiceTemplate.deleteByQuery(indexName, b -> b.query(q -> q.ids(i -> i.values(values))))
                                  .mapEmpty();
    }

}
