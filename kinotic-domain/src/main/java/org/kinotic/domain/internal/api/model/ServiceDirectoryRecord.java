package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;

import java.util.List;

/**
 * A directory entry as the Elasticsearch strategy stores it: the contract the {@link ServiceDirectoryEntry}
 * describes, the hash an unchanged contract write is told apart by, and what the contract names of the
 * authorization model, which the services deciding a type together are found by.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServiceDirectoryRecord extends ServiceDirectoryEntry {
    /**
     * The hash of the contract fields as last published.
     */
    private String contractHash;

    /**
     * The resource types the contract decides: the type it declares, and each type a function requires a
     * permission of.
     */
    private List<String> authzTypes;

    /**
     * The type the contract places its declared type inside, or null when it names none.
     */
    private String authzParent;

    /**
     * The model names of the permissions the contract's functions require, each {@code <type>_<permission>}.
     */
    private List<String> authzPermissions;
}
