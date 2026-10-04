package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.model.Watched;
import org.kinotic.domain.api.model.WatchedState;

/**
 * A directory entry as the Elasticsearch strategy stores it: the contract the {@link ServiceDirectoryEntry}
 * describes, the hash an unchanged contract write is told apart by, and what the platform keeps on every watched
 * record, so a contract write marks the entry for the reconcile master and names the authorization store the
 * entry belongs to as its parent.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServiceDirectoryRecord extends ServiceDirectoryEntry implements Watched {
    /**
     * The hash of the contract fields as last published.
     */
    private String contractHash;
    private WatchedState state = new WatchedState();
}
