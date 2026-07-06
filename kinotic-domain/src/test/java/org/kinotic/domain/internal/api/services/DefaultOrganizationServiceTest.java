package org.kinotic.domain.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Organization;

import java.util.List;

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

        assertEquals("acme_rockets", organization.getId());
        assertNotNull(organization.getCreated());
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

        assertEquals("kinetic_corp", organization.getId());
    }

    @Test
    void skipsGuardForExplicitPlatformIds() {
        Organization organization = new Organization().setId("kinotic-test").setName("kinotic-test");

        service.beforeSave(organization).join();

        assertEquals("kinotic-test", organization.getId());
    }
}
