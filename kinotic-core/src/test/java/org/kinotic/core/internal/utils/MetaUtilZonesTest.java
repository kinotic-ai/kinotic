package org.kinotic.core.internal.utils;

import org.junit.jupiter.api.Test;
import org.kinotic.core.internal.utils.zonefixtures.PackageZonedService;
import org.kinotic.core.internal.utils.zonefixtures.TypeZonedService;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies zone resolution precedence: type level {@code @Zones}, then the package-info
 * declaration, then nothing (callers apply the system default).
 */
public class MetaUtilZonesTest {

    @Test
    public void typeLevelZonesWinOverThePackage() {
        assertArrayEquals(new String[]{"billing", "system"}, MetaUtil.getZones(TypeZonedService.class));
    }

    @Test
    public void packageInfoZonesApplyWhenTheTypeHasNone() {
        assertArrayEquals(new String[]{"api"}, MetaUtil.getZones(PackageZonedService.class));
    }

    @Test
    public void noDeclarationAnywhereResolvesToNull() {
        assertNull(MetaUtil.getZones(MetaUtilZonesTest.class));
    }

}
