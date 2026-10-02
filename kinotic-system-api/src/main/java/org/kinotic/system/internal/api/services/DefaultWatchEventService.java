package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.system.api.services.WatchEventService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultWatchEventService implements WatchEventService {

    private final WatchEventRepository watchEventRepository;

    @Override
    public Future<Page<WatchEvent>> findAll(Pageable pageable) {
        return watchEventRepository.findAll(pageable);
    }

}
