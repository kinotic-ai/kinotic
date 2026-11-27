package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchedType;

import java.util.List;

/**
 * The ledger of every watched record on the platform, as the console reads it.
 */
@Publish
public interface WatchEventService {

    /**
     * Lists what happened to every watched record, newest first: each change of what a record should
     * be and of what it reports, each mark set beside them, and each status a run passed through, with
     * what caused it. Each entry names the record by its name when written, beside its id.
     *
     * @param types    the kinds of record to list the entries of, every kind when null or empty
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries
     */
    Future<Page<WatchEvent>> findAll(List<WatchedType> types, Pageable pageable);

}
