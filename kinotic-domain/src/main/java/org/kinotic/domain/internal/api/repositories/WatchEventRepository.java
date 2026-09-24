package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * The ledger of what happened to every watched record, an append-only data stream.
 */
@Component
@RequiredArgsConstructor
public class WatchEventRepository {

    public static final String DATA_STREAM = "kinotic_watch_event";

    private final CrudServiceTemplate crudServiceTemplate;

    /**
     * Appends the event to the ledger.
     *
     * @param event what happened
     * @return a future that completes once the entry is accepted
     */
    public Future<Void> record(WatchEvent event) {
        Validate.notNull(event, "event cannot be null");
        return crudServiceTemplate.appendToDataStream(DATA_STREAM, event).mapEmpty();
    }
}
