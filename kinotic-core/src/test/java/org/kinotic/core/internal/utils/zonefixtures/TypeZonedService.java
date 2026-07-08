package org.kinotic.core.internal.utils.zonefixtures;

import org.kinotic.core.api.annotations.Zones;

/**
 * The type level declaration overrides the package-info declaration.
 */
@Zones({"billing", "system"})
public interface TypeZonedService {
}
