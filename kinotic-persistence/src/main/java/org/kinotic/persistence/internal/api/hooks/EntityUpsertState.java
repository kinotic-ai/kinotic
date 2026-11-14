package org.kinotic.persistence.internal.api.hooks;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessorState;
import tools.jackson.core.JsonParser;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * What the upsert processing of entities collects while it streams one save or update: the values the
 * {@link UpsertFieldTransformer}s extract from the root object being written, and the tenants named across
 * every root object of the input.
 */
@Getter
@RequiredArgsConstructor
public class EntityUpsertState extends JsonStreamProcessorState {

    private final EntityContext context;
    @Getter(AccessLevel.NONE)
    private final JsonMapper jsonMapper;
    private final ByteArrayBuilder buffer = new ByteArrayBuilder();
    private final List<String> tenantsSelected = new ArrayList<>();

    private String id;
    @Setter
    private String tenantId;
    @Setter
    private String version;

    /**
     * Reads the value of the field whose name the parser is on, leaving the parser on the value's last token.
     */
    public <T> T readFieldValue(JsonParser parser, Class<T> type) {
        parser.nextToken();
        return jsonMapper.readValue(parser, type);
    }

    /**
     * Records the id of the root object being written.
     *
     * @throws IllegalArgumentException if the root object already named an id
     */
    public void recordId(String id) {
        // reached by a payload that repeats the id field, since a published definition holds a single id field
        if(this.id != null){
            throw new IllegalArgumentException("Found multiple id fields in entity");
        }
        this.id = id;
    }

    /**
     * Clears what was collected from the root object just emitted.
     */
    public void resetRootObject() {
        buffer.reset();
        id = null;
        tenantId = null;
        version = null;
    }
}
