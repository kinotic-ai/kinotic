package org.kinotic.domain.internal.api.ee.license;

import io.vertx.core.Future;

/**
 * The key renewal Minds Ignited's license server publishes as {@code org.kinotic.license.LicenseRenewalService} in
 * the {@code licensee} zone, which a cluster calls signed in with its license key.
 */
public interface LicenseKeyRenewalService {

    /**
     * @return the newest key of the cluster the caller's key was issued for, issued first when the license is paid
     *         beyond the caller's newest key; fails with the reason when no newer key comes, such as a past-due license
     */
    Future<String> renew();
}
