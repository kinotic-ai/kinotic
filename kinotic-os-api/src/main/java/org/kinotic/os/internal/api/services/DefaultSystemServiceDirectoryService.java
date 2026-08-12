package org.kinotic.os.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.os.api.services.SystemServiceDirectoryService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultSystemServiceDirectoryService implements SystemServiceDirectoryService {

    private final ServiceDirectory serviceDirectory;

    @Override
    public CompletableFuture<Page<ServiceDirectoryEntry>> findEntries(Pageable pageable) {
        // null scope = the system view: every entry regardless of owner
        return serviceDirectory.findEntriesScopedTo(null, null, pageable)
                               .toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Void> verifyLiveness(String serviceAddress) {
        return serviceDirectory.verifyLiveness(serviceAddress)
                               .toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Void> reconcileLiveness() {
        return serviceDirectory.reconcileLiveness()
                               .toCompletionStage().toCompletableFuture();
    }
}
