/**
 * Billing of organizations for Kinotic Cloud: the service an organization's members reach it through, and the
 * management server's way to the system server's billing operations. Present only where
 * {@code kinotic.managementApi.billing.enabled}.
 */
@Version("1.0.0")
@Zone(DomainUtil.MANAGEMENT_API_ZONE)
package org.kinotic.management.api.services.billing;

import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
