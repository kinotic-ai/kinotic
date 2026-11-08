package org.kinotic.domain.internal.api.ee.license;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * The Kinotic Enterprise License key the cluster runs on, shared by every node.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class InstalledLicenseKey implements Identifiable<String> {

    private String id;

    /** The license key, as Minds Ignited issued it. */
    private String licenseKey;

    /** When the key was installed. */
    private Date installed;
}
