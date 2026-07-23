package org.kinotic.core.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies zone aware addressing of a {@link ServiceIdentifier}.
 */
public class ServiceIdentifierTest {

    @Test
    public void qualifiedNameIsZonePrefixed() {
        ServiceIdentifier identifier = new ServiceIdentifier("api", "org.kinotic.os.api.services.iam", "MemberService", null, "1.0.0");
        assertEquals("api~org.kinotic.os.api.services.iam.MemberService", identifier.qualifiedName());
        assertEquals("srv://api~org.kinotic.os.api.services.iam.MemberService#1.0.0", identifier.cri().raw());
        assertEquals("api", identifier.cri().zone());
        assertEquals("org.kinotic.os.api.services.iam.MemberService", identifier.cri().resourceName());
    }

    @Test
    public void platformZoneAddresses() {
        ServiceIdentifier identifier = new ServiceIdentifier("os-api", "com.example", "LogManager", null, "1.0.0");
        assertEquals("os-api~com.example.LogManager", identifier.qualifiedName());
        assertEquals("srv://os-api~com.example.LogManager#1.0.0", identifier.cri().raw());
    }

    @Test
    public void scopeAndMissingNamespaceRoundTrip() {
        ServiceIdentifier identifier = new ServiceIdentifier("system", null, "VmManager", "node1", "0.1.0");
        assertEquals("system~VmManager", identifier.qualifiedName());
        assertEquals("srv://node1@system~VmManager#0.1.0", identifier.cri().raw());
    }

    @Test
    public void noZoneMeansTheUnZonedAddress() {
        ServiceIdentifier identifier = new ServiceIdentifier(null, "com.example", "LegacyService", null, "1.0.0");
        assertEquals("com.example.LegacyService", identifier.qualifiedName());
        assertEquals("srv://com.example.LegacyService#1.0.0", identifier.cri().raw());
        assertNull(identifier.cri().zone());
    }

    @Test
    public void identityIncludesTheZone() {
        ServiceIdentifier api = new ServiceIdentifier("api", "com.example", "LogManager", null, "1.0.0");
        ServiceIdentifier system = new ServiceIdentifier("system", "com.example", "LogManager", null, "1.0.0");
        assertNotEquals(api, system);
        assertEquals(api, new ServiceIdentifier("api", "com.example", "LogManager", null, "1.0.0"));
    }

    @Test
    public void invalidPartsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("Bad.Zone", "com.example", "Svc", null, "1.0.0"));
        // the name is the final label of the address, so it can never contain a dot
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("api", "com.example", "Svc.Extra", null, "1.0.0"));
        // an underscore in a namespace segment would make the CRI an invalid URI
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("api", "com.my_example", "Svc", null, "1.0.0"));
        // '~' delimits the zone in a CRI, so neither the namespace nor the name may carry one
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("api", "com.exa~mple", "Svc", null, "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("api", "com.example", "Sv~c", null, "1.0.0"));
    }

}
