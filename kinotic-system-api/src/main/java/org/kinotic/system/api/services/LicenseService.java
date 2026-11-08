package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.system.api.model.LicenseDetails;

/**
 * The Kinotic Enterprise License key the whole cluster runs on, which system users read and replace without
 * restarting a server.
 * <p>
 * Published in the system zone, which only SYSTEM participants and the system's own cluster members may address.
 */
@Publish
public interface LicenseService {

    /** The license the cluster runs on, or {@code null} when no valid key is installed. */
    Future<LicenseDetails> findCurrent();

    /**
     * Installs {@code licenseKey} on every node in place of the installed key, completing once every node runs on it.
     * Fails when the key is not one Minds Ignited signed, names no licensee or expiry, or ended its grace period.
     *
     * @param licenseKey a Kinotic Enterprise License key, as Minds Ignited issued it
     * @return the license the key holds
     */
    Future<LicenseDetails> install(String licenseKey);
}
