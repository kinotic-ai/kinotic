package org.kinotic.core.internal.utils;

import org.junit.jupiter.api.Test;
import org.kinotic.core.internal.utils.zonefixtures.PackageZonedService;
import org.kinotic.core.internal.utils.zonefixtures.TypeZonedService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies zone resolution precedence: type level {@code @Zone}, then the package-info
 * declaration, then nothing (callers apply the system default).
 */
public class MetaUtilZonesTest {

    @Test
    public void typeLevelZoneWinsOverThePackage() {
        assertEquals("billing", MetaUtil.getZone(TypeZonedService.class));
    }

    @Test
    public void packageInfoZoneAppliesWhenTheTypeHasNone() {
        assertEquals("api", MetaUtil.getZone(PackageZonedService.class));
    }

    @Test
    public void noDeclarationAnywhereResolvesToNull() {
        assertNull(MetaUtil.getZone(MetaUtilZonesTest.class));
    }

}
