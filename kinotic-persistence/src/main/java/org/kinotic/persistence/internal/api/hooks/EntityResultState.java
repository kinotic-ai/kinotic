package org.kinotic.persistence.internal.api.hooks;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.persistence.internal.api.services.ElasticVersion;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessorState;

/**
 * What writing a stored entity out to its caller carries through one call: the version the entity is at, and the
 * output its document is written to.
 *
 * @param <O> the type of the output
 */
@Getter
@RequiredArgsConstructor
public class EntityResultState<O> extends JsonStreamProcessorState {

    private final ElasticVersion version;
    private final O output;
}
