package org.kinotic.core.api.directory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An entry stored in the directory by some node, emitted on the event fabric once it is visible to the
 * directory's queries. The scope says whose contract changed: both ids null for a platform service, an
 * organization for an organization-level one, both for an application's. Delivery is the fabric's: at most
 * once, to every receiver on every node, so a receiver deriving state from these rebuilds it from the directory
 * on a period of its own.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDirectoryChange {

    private String entryId;
    private String organizationId;
    private String applicationId;

}
