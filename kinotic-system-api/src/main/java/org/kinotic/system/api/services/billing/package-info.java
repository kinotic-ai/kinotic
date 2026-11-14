/**
 * Billing of organizations for Kinotic Cloud through Minds Ignited's Stripe account: the plans system users offer,
 * and the operations the management server performs for an organization. Present only where
 * {@code kinotic.managementApi.billing.enabled}.
 */
@Version("1.0.0")
@Zone(DomainUtil.SYSTEM_API_ZONE)
package org.kinotic.system.api.services.billing;

import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
