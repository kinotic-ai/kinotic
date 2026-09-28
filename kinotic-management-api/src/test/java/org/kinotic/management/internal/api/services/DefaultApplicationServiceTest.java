package org.kinotic.management.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers id handling in {@link DefaultApplicationService#beforeSave}: minting the id from the
 * slugified name, rejecting ids that are not lowercase letters, digits, and interior dashes,
 * rejecting the reserved {@code system} label, and rejecting ids that cannot form the
 * application's host label. beforeSave reads no collaborator but the caller's organization, so
 * no other is given.
 */
class DefaultApplicationServiceTest {

    private static final String CALLER_ORG = "acme";

    private final DefaultApplicationService service = new DefaultApplicationService(null, null, null, callerContext());

    private static SecurityContext callerContext() {
        OrganizationParticipant participant = mock(OrganizationParticipant.class);
        when(participant.getOrganizationId()).thenReturn(CALLER_ORG);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.requireParticipant(OrganizationParticipant.class)).thenReturn(participant);
        return securityContext;
    }

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
                     () -> service.beforeSave(new Application("System Api", "desc")));
        // an update keeps the caller's id rather than re-minting it, so that path is guarded too
        Application existing = new Application("Anything", "desc");
        existing.setId("system-api");
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
    void rejectsIdsHoldingTheHostLabelSeparator() {
        Application application = new Application("Orders App", "desc");
        application.setId("orders--app");

        assertThrows(IllegalArgumentException.class, () -> service.beforeSave(application));
    }

    @Test
    void rejectsIdsWhoseHostLabelIsLongerThanDnsAllows() {
        // "acme--" leaves 57 characters of the 63 a label may hold
        Application fits = new Application("Orders App", "desc");
        fits.setId("a".repeat(57));
        assertDoesNotThrow(() -> service.beforeSave(fits));

        Application tooLong = new Application("Orders App", "desc");
        tooLong.setId("a".repeat(58));
        assertThrows(IllegalArgumentException.class, () -> service.beforeSave(tooLong));
    }

    @Test
    void rejectsAMissingName() {
        Application application = new Application(null, "desc");
        application.setId("orders-app");

        assertThrows(NullPointerException.class, () -> service.beforeSave(application));
    }
}
