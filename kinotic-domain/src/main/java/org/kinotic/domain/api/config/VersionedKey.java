package org.kinotic.domain.api.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One version of a key in a {@link VersionedKeySet}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class VersionedKey {

    /** Stable, human-readable version identifier (e.g., "v1", "v2"). */
    private String id;

    /** Base64-encoded key material. */
    private String key;
}
