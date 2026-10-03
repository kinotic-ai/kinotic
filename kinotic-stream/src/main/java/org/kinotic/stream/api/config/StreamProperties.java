package org.kinotic.stream.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Stream storage settings for this node.
 */
@Getter
@Setter
@Accessors(chain = true)
public class StreamProperties {

    /**
     * The local directory this node keeps its stream shards and consumer offsets in. It must be on a
     * local disk: the shards are memory-mapped files, which network file systems do not support.
     */
    @NotBlank
    private String dataDirectory;

}
