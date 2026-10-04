package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.internal.api.model.DeviceCodeGrant;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeviceCodeGrantRepository extends AbstractRepository<DeviceCodeGrant> {

    // A grant is bound to the first approver in the shard operation that reads whether it is bound already
    private static final String APPROVE = """
            if (ctx._source.identityId != null) {
                ctx.op = 'noop';
            } else {
                ctx._source.identityId = params.identityId;
            }
            """;
    // Redeeming a grant deletes it in one shard operation, so of two polls redeeming one grant the second
    // finds no document, which the engine answers with a missing-document error
    private static final String CONSUME = "ctx.op = 'delete'";

    public DeviceCodeGrantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_device_code_grant", DeviceCodeGrant.class, crudServiceTemplate);
    }

    /** Finds the grant whose device code hashes to {@code deviceCodeHash}, or {@code null} if none matches. */
    public Future<DeviceCodeGrant> findByDeviceCodeHash(String deviceCodeHash) {
        return findFirst(b -> b.query(termFilter("deviceCodeHash", deviceCodeHash)));
    }

    /** Finds the grant with the given {@code userCode}, or {@code null} if none matches. */
    public Future<DeviceCodeGrant> findByUserCode(String userCode) {
        return findFirst(b -> b.query(termFilter("userCode", userCode)));
    }

    /**
     * Binds the approving identity to the grant unless one is bound already. Visible to search on completion.
     *
     * @param id         the grant
     * @param identityId the identity approving it
     * @return true when this call bound the identity, false when the grant was approved already
     */
    public Future<Boolean> approve(String id, String identityId) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, APPROVE, Map.of("identityId", identityId));
    }

    /**
     * Redeems the grant, deleting it, unless it is gone already. Visible to search on completion.
     *
     * @param id the grant
     * @return true when this call redeemed it, false when another had
     */
    public Future<Boolean> consume(String id) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, CONSUME, Map.of())
                                  .recover(error -> CrudServiceTemplate.isDocumentMissing(error)
                                          ? Future.succeededFuture(false)
                                          : Future.failedFuture(error));
    }
}
