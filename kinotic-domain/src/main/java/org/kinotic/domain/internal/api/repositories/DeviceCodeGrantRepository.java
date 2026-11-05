package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.model.DeviceCodeGrant;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class DeviceCodeGrantRepository extends AbstractRepository<DeviceCodeGrant> {

    // A grant is bound to the first approver in the shard operation that reads whether it is bound already
    private static final String APPROVE = """
            if (ctx._source.identityId != null) {
                ctx.op = 'noop';
            } else {
                ctx._source.identityId = params.identityId;
                ctx._source.organizationId = params.organizationId;
                ctx._source.applicationId = params.applicationId;
            }
            """;

    public DeviceCodeGrantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_device_code_grant", DeviceCodeGrant.class, crudServiceTemplate);
    }

    /** Deletes every grant user {@code identityId} approved, visible to search when the future completes. */
    public Future<Void> deleteByIdentity(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return deleteByQuery(b -> b.query(termFilter("identityId", identityId)));
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
     * Records when the grant was last polled, leaving its approval as a concurrent {@link #approve} wrote it.
     * Visible to search on completion.
     *
     * @param id       the grant
     * @param polledAt when it was polled
     * @return a {@link Future} that completes when the time is recorded, or fails when the grant no longer exists
     */
    public Future<Void> recordPoll(String id, Date polledAt) {
        return crudServiceTemplate.partialUpdateSync(indexName, id, Map.of("lastPolledAt", polledAt), false);
    }

    /**
     * Binds the approving identity, in the scope it approved from, to the grant unless the grant is approved
     * already. Of concurrent calls for one grant exactly one binds it. Visible to search on completion.
     *
     * @param id         the grant
     * @param identityId the identity approving it
     * @param scope      the scope the identity approved from
     * @return true when this call bound the identity, false when the grant was approved already
     */
    public Future<Boolean> approve(String id, String identityId, ParticipantScope scope) {
        // a HashMap, since an approver in organization or system scope has no application id and Map.of rejects null
        Map<String, Object> params = new HashMap<>();
        params.put("identityId", identityId);
        params.put("organizationId", scope.organizationId());
        params.put("applicationId", scope.applicationId());
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, APPROVE, params);
    }
}
