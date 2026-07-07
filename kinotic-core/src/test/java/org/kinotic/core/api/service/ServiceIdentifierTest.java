package org.kinotic.core.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies zone aware addressing of a {@link ServiceIdentifier}.
 */
public class ServiceIdentifierTest {

    @Test
    public void resourceNameIsZonePrefixed() {
        ServiceIdentifier identifier = new ServiceIdentifier("api", "org.kinotic.os.api.services.iam", "MemberService", null, "1.0.0");
        assertEquals("org.kinotic.os.api.services.iam.MemberService", identifier.qualifiedName());
        assertEquals("api.org.kinotic.os.api.services.iam.MemberService", identifier.resourceName());
        assertEquals("srv://api.org.kinotic.os.api.services.iam.MemberService#1.0.0", identifier.cri().raw());
    }

    @Test
    public void scopeAndMissingNamespaceRoundTrip() {
        ServiceIdentifier identifier = new ServiceIdentifier("system", null, "VmManager", "node1", "0.1.0");
        assertEquals("VmManager", identifier.qualifiedName());
        assertEquals("system.VmManager", identifier.resourceName());
        assertEquals("srv://node1@system.VmManager#0.1.0", identifier.cri().raw());
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
        // commons-lang3 Validate.notEmpty semantics: NPE for null, IAE for empty
        assertThrows(NullPointerException.class, () -> new ServiceIdentifier(null, "com.example", "Svc", null, "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("Bad.Zone", "com.example", "Svc", null, "1.0.0"));
        // the name is the final label of the address, so it can never contain a dot
        assertThrows(IllegalArgumentException.class, () -> new ServiceIdentifier("api", "com.example", "Svc.Extra", null, "1.0.0"));
    }

}
