package org.kinotic.authz.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.authz.api.services.StoreRelationships;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultRelationshipService implements RelationshipService {

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;

    @Override
    public StoreRelationships platform() {
        return new DefaultStoreRelationships(fga, stores::platformStoreId);
    }

    @Override
    public StoreRelationships store(String storeId) {
        return new DefaultStoreRelationships(fga, () -> Future.succeededFuture(storeId));
    }
}
