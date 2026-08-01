package org.kinotic.domain.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Organization;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers id minting in {@link DefaultOrganizationService#beforeSave}: slug derivation and
 * the reserved platform prefix guard. The repository is unused by beforeSave, so none is given.
 */
class DefaultOrganizationServiceTest {

    private final DefaultOrganizationService service = new DefaultOrganizationService(null);

    @Test
    void mintsIdFromSlugifiedName() {
        Organization organization = new Organization().setName("Acme Rockets");

        service.beforeSave(organization).join();

        assertEquals("acme-rockets", organization.getId());
        assertNotNull(organization.getCreated());
    }

    @Test
    void mintsLabelSafeIdsFromPunctuatedNames() {
        Organization organization = new Organization().setName("Acme Inc.");

        service.beforeSave(organization).join();

        assertEquals("acme-inc", organization.getId());
    }

    @Test
    void rejectsNamesThatMintReservedIds() {
        for (String name : List.of("Kinotic", "Kinotic System", "kinotic-system", "KINOTIC ops")) {
            assertThrows(IllegalArgumentException.class,
                         () -> service.beforeSave(new Organization().setName(name)),
                         "expected '" + name + "' to be rejected");
        }
    }

    @Test
    void allowsNamesNearTheReservedPrefix() {
        Organization organization = new Organization().setName("Kinetic Corp");

        service.beforeSave(organization).join();

        assertEquals("kinetic-corp", organization.getId());
    }

    @Test
    void rejectsIdsThatCollideWithTheSystemZone() {
        assertThrows(IllegalArgumentException.class,
                     () -> service.beforeSave(new Organization().setName("System")));
        // an update keeps the caller's id rather than re-minting it, so that path is guarded too
        assertThrows(IllegalArgumentException.class,
                     () -> service.beforeSave(new Organization().setId("system").setName("Anything")));
    }

    @Test
    void allowsNamesMatchingTheOtherPlatformZones() {
        // the kinotic-app prefix keeps application zones clear of these, so they stay usable names
        for (String name : List.of("App API", "OS API", "App")) {
            assertDoesNotThrow(() -> service.beforeSave(new Organization().setName(name)),
                               "expected '" + name + "' to be allowed");
        }
    }

    @Test
    void rejectsExplicitReservedIds() {
        Organization organization = new Organization().setId("kinotic-test").setName("Anything");

        assertThrows(IllegalArgumentException.class, () -> service.beforeSave(organization));
    }
}
