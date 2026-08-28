package org.kinotic.os.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers id handling in {@link DefaultApplicationService#beforeSave}: minting the id from the
 * slugified name, rejecting ids that are not lowercase letters, digits, and interior dashes,
 * and rejecting the reserved {@code system} label. The collaborators are unused by beforeSave,
 * so none are given.
 */
class DefaultApplicationServiceTest {

    private final DefaultApplicationService service = new DefaultApplicationService(null, null, null, null);

    @Test
    void mintsIdFromSlugifiedName() {
        Application application = new Application("Orders App", "desc");

        service.beforeSave(application).await();

        assertEquals("orders-app", application.getId());
        assertNotNull(application.getUpdated());
    }

    @Test
    void keepsAnExistingId() {
        Application application = new Application("Orders App", "desc");
        application.setId("orders-app-v2");

        service.beforeSave(application).await();

        assertEquals("orders-app-v2", application.getId());
    }

    @Test
    void rejectsExistingIdsThatAreInvalid() {
        for (String id : List.of("orders.app", "Orders-App", "orders app", "orders.*")) {
            Application application = new Application("Orders App", "desc");
            application.setId(id);
            assertThrows(IllegalArgumentException.class,
                         () -> service.beforeSave(application),
                         "expected '" + id + "' to be rejected");
        }
    }

    @Test
    void rejectsIdsThatCollideWithTheSystemZone() {
        assertThrows(IllegalArgumentException.class,
                     () -> service.beforeSave(new Application("System", "desc")));
        // an update keeps the caller's id rather than re-minting it, so that path is guarded too
        Application existing = new Application("Anything", "desc");
        existing.setId("system");
        assertThrows(IllegalArgumentException.class, () -> service.beforeSave(existing));
    }

    @Test
    void allowsNamesMatchingTheOtherPlatformZones() {
        // an id only ever lands after the app zone prefix, so these cannot collide with a zone
        for (String name : List.of("App API", "OS API", "App")) {
            assertDoesNotThrow(() -> service.beforeSave(new Application(name, "desc")),
                               "expected '" + name + "' to be allowed");
        }
    }

    @Test
    void rejectsAMissingName() {
        Application application = new Application(null, "desc");
        application.setId("orders-app");

        assertThrows(NullPointerException.class, () -> service.beforeSave(application));
    }
}
