package org.kinotic.core.internal.utils;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.internal.utils.publishfixtures.DefaultEngineFixtureService;
import org.kinotic.core.internal.utils.publishfixtures.DiamondEngineFixtureService;
import org.kinotic.core.internal.utils.publishfixtures.WatchFixtureService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link MetaUtil#getInterfaceDeclaringAnnotation} walks the full interface
 * hierarchy: an annotation on a superinterface is found when the bean class only
 * implements a sub-interface, and a diamond yields the annotated interface once.
 */
public class MetaUtilPublishDiscoveryTest {

    @Test
    public void annotationOnASuperinterfaceIsFoundThroughTheExtendsChain() {
        List<Class<?>> found = MetaUtil.getInterfaceDeclaringAnnotation(DefaultEngineFixtureService.class, Publish.class);

        assertEquals(List.of(WatchFixtureService.class), found);
    }

    @Test
    public void aDiamondYieldsTheAnnotatedInterfaceOnce() {
        List<Class<?>> found = MetaUtil.getInterfaceDeclaringAnnotation(DiamondEngineFixtureService.class, Publish.class);

        assertEquals(List.of(WatchFixtureService.class), found);
    }

    @Test
    public void aClassWithNoAnnotatedInterfaceYieldsNothing() {
        assertTrue(MetaUtil.getInterfaceDeclaringAnnotation(String.class, Publish.class).isEmpty());
    }

}
