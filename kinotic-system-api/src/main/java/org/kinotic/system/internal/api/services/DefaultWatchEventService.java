package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.system.api.services.WatchEventService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultWatchEventService implements WatchEventService {

    private final WatchEventRepository watchEventRepository;

    @Override
    public Future<Page<WatchEvent>> findAll(List<WatchedType> types, Pageable pageable) {
        return watchEventRepository.findAll(types, pageable);
    }

}
