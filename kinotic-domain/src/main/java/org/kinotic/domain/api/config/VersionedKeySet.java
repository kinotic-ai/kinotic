package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * A rotation-aware set of keys. Exactly one key is active at a time — used for minting,
 * writing, or any operation that chooses a single key. All keys in the set are valid for
 * verification, reading, or derivation fallback.
 * <p>
 * JSON shape on disk:
 * <pre>
 * { "activeKeyId": "v2", "keys": [ {"id": "v2", "key": "&lt;b64&gt;"}, {"id": "v1", "key": "&lt;b64&gt;"} ] }
 * </pre>
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VersionedKeySet {

    private String activeKeyId;
    private List<VersionedKey> keys = new ArrayList<>();

    public VersionedKey getActive() {
        for (VersionedKey entry : keys) {
            if (entry.getId().equals(activeKeyId)) {
                return entry;
            }
        }
        throw new IllegalStateException("Active key id '" + activeKeyId + "' not present in keys list");
    }

    public VersionedKey findById(String id) {
        for (VersionedKey entry : keys) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
