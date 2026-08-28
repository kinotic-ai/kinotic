/**
 * Grind service contracts, including the interfaces published for remote access.
 */
@Version("1.0.0")
// Published grind services are UI-facing reads, so they register in the management-api zone,
// which organization participants may address; the system-api zone is unreachable for them
@Zone(DomainUtil.MANAGEMENT_API_ZONE)
package org.kinotic.grindv2.api.services;

import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
