package org.kinotic.continuum.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contains information about this server
 * Created by Navíd Mitchell 🤪 on 4/5/24.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ServerInfo {
    /**
     * The UUID for this node
     */
    private String nodeId;
    /**
     * Returns a human-readable name for this node
     */
    private String nodeName;
}
