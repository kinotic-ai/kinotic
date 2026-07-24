package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.api.model.DeviceCodeGrant;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DeviceCodeGrantRepository extends AbstractRepository<DeviceCodeGrant> {

    public DeviceCodeGrantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_device_code_grant", DeviceCodeGrant.class, crudServiceTemplate);
    }

    /** Finds the grant whose device code hashes to {@code deviceCodeHash}, or {@code null} if none matches. */
    public CompletableFuture<DeviceCodeGrant> findByDeviceCodeHash(String deviceCodeHash) {
        return findFirst(b -> b.query(termFilter("deviceCodeHash", deviceCodeHash)));
    }

    /** Finds the grant with the given {@code userCode}, or {@code null} if none matches. */
    public CompletableFuture<DeviceCodeGrant> findByUserCode(String userCode) {
        return findFirst(b -> b.query(termFilter("userCode", userCode)));
    }
}
