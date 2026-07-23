

package org.kinotic.core.internal.api;

import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CRIs are backed by {@link java.net.URI}, whose server-based authority parsing rejects
 * underscores. Zone labels never carry one (the grammar forbids it), but a namespace segment
 * still can, so these tests pin that CRIs resolve their scope, zone, and resourceName from an
 * underscore-bearing address regardless.
 * Created by navid on 1/23/20
 */
public class CRITests {

    private static final String SERVICE_NAME = "org.kinotic.tests.testservice";
    private static final String SERVICE_SCOPE = "e35f51d0-6c6e-4b58-9b9d-f5b53dd978b0";
    private static final String SERVICE_VERSION = "0.1.0";
    private static final String SERVICE_LITERAL1 = EventConstants.SERVICE_DESTINATION_SCHEME
                                                        + "://"
                                                        + SERVICE_NAME
                                                        + "#"
                                                        + SERVICE_VERSION;

    private static final String SERVICE_LITERAL2 = EventConstants.SERVICE_DESTINATION_SCHEME
                                                        + "://"
                                                        + SERVICE_SCOPE
                                                        + "@"
                                                        + SERVICE_NAME
                                                        + "#"
                                                        + SERVICE_VERSION;

    // A namespace segment carries an underscore, which java.net.URI will not accept as a hostname
    private static final String ZONE = "os-api";
    private static final String RESOURCE_NAME = "org.kinotic.my_service.itestservice";
    private static final String ZONED_NAME = ZONE + "~" + RESOURCE_NAME;

    @Test
    public void testRawCRI1(){
        validateCRI(CRI.create(SERVICE_LITERAL1), false);
    }

    @Test
    public void testRawCRI2(){
        validateCRI(CRI.create(SERVICE_LITERAL2), true);
    }

    @Test
    public void parsesZonedResourceNameWithUnderscore(){
        CRI cri = CRI.create("srv://" + ZONED_NAME + "/testMethodWithString#1.0.0");

        assertEquals(ZONE, cri.zone());
        assertEquals(RESOURCE_NAME, cri.resourceName());
        assertEquals("srv://" + ZONED_NAME, cri.baseResource());
        assertEquals("/testMethodWithString", cri.path());
        assertEquals("1.0.0", cri.version());
        assertNull(cri.scope());
    }

    @Test
    public void parsesScopedZonedResourceNameWithUnderscore(){
        CRI cri = CRI.create("srv://node1@" + ZONED_NAME + "/save#1.0.0");

        assertEquals("node1", cri.scope());
        assertEquals(ZONE, cri.zone());
        assertEquals(RESOURCE_NAME, cri.resourceName());
        assertEquals("srv://node1@" + ZONED_NAME, cri.baseResource());
    }

    @Test
    public void buildsZonedResourceNameFromComponents(){
        CRI cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, null, ZONED_NAME, "/save", "1.0.0");

        assertEquals(ZONE, cri.zone());
        assertEquals(RESOURCE_NAME, cri.resourceName());
        assertEquals("srv://" + ZONED_NAME, cri.baseResource());
        // The raw form round-trips back to a CRI that resolves the same zone and resourceName
        assertEquals(ZONE, CRI.create(cri.raw()).zone());
        assertEquals(RESOURCE_NAME, CRI.create(cri.raw()).resourceName());
    }

    @Test
    public void buildsScopedZonedResourceNameFromComponents(){
        CRI cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, "node1", ZONED_NAME, "/save", "1.0.0");

        assertEquals("node1", cri.scope());
        assertEquals(ZONE, cri.zone());
        assertEquals(RESOURCE_NAME, cri.resourceName());
        assertEquals("srv://node1@" + ZONED_NAME, cri.baseResource());
    }

    @Test
    public void scopesCarryingTheZoneDelimiterAreRejected(){
        // '~' delimits the zone, so a scope containing one could only be crafted to confuse parsing
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create("srv://evil~x@" + ZONED_NAME + "/save#1.0.0"));
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, "evil~x", ZONED_NAME, "/save", "1.0.0"));
    }

    @Test
    public void strayScopeDelimitersAreRejected(){
        // '@' delimits the scope: component parts may never carry one, and a raw form
        // may have at most one
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, "a@b", ZONED_NAME, "/save", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, null, "x@" + ZONED_NAME, "/save", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create("srv://a@b@" + ZONED_NAME + "/save#1.0.0"));
    }

    @Test
    public void secondZoneDelimiterInTheHostPartIsRejected(){
        // only the first '~' is the delimiter; a resourceName carrying another is always a mistake
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create("srv://" + ZONED_NAME + "~extra/save#1.0.0"));
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, null, ZONED_NAME + "~extra", "/save", "1.0.0"));
    }

    @Test
    public void unZonedCRIHasNoZone(){
        CRI cri = CRI.create(SERVICE_LITERAL1);

        assertNull(cri.zone());
        assertEquals(SERVICE_NAME, cri.resourceName());
    }

    @Test
    public void rawFormPreservesCaseInEveryPart(){
        CRI cri = CRI.create("srv://Node1@os-api~org.kinotic.tests.TestService/testMethodWithString#1.0.0");

        assertEquals("srv", cri.scheme());
        assertEquals("Node1", cri.scope());
        assertEquals("os-api", cri.zone());
        assertEquals("org.kinotic.tests.TestService", cri.resourceName());
        assertEquals("/testMethodWithString", cri.path());
        assertEquals("srv://Node1@os-api~org.kinotic.tests.TestService/testMethodWithString#1.0.0", cri.raw());
    }

    @Test
    public void componentFormPreservesCaseInEveryPart(){
        CRI cri = CRI.create("srv", "Node1", "os-api~org.kinotic.tests.TestService", "/testMethodWithString", "1.0.0");

        assertEquals("srv", cri.scheme());
        assertEquals("Node1", cri.scope());
        assertEquals("os-api", cri.zone());
        assertEquals("org.kinotic.tests.TestService", cri.resourceName());
        assertEquals("/testMethodWithString", cri.path());
        // both construction paths produce the same raw form
        assertEquals(CRI.create(cri.raw()), cri);
    }

    private void validateCRI(CRI cri, boolean checkScope){
        Validate.isTrue(cri.resourceName().equals(SERVICE_NAME), "CRI resourceName does not match expected got "+ cri.resourceName());
        Validate.isTrue(cri.version().equals(SERVICE_VERSION), "CRI version does not match expected got "+ cri.version());
        if(checkScope){
            Validate.isTrue(cri.scope().equals(SERVICE_SCOPE), "CRI scope does not match expected got "+ cri.scope());
        }
    }

}
