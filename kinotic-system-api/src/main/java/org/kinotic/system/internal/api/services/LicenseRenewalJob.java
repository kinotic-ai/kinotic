package org.kinotic.system.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.services.EntitlementService;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.services.SystemJob;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The renewal of the cluster's license key with {@link EntitlementService#renew()} as a system job.
 * It runs daily, and an operator can run it ahead of the schedule; the renewal asks the license
 * server only once the key nears its expiry. A run fails when the key could not be renewed, and the
 * next day's run tries again.
 */
@Component
@RequiredArgsConstructor
public class LicenseRenewalJob implements SystemJob {

    private final EntitlementService entitlementService;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Renews the cluster's license key with Minds Ignited's license server once it nears its expiry")
                            .name("license-renewal")
                            .version("1")
                            .every(Duration.ofDays(1))
                            // the license holds the key, so the task completes without a value to record
                            .task(Tasks.fromCallable("Renew the license key", () -> entitlementService.renew().mapEmpty()));
    }

}
