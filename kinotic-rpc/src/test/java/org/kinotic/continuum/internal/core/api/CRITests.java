

package org.kinotic.continuum.internal.core.api;

import org.kinotic.rpc.api.event.CRI;
import org.kinotic.rpc.api.event.EventConstants;
import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.Test;

/**
 * CRI's internally are implemented using the Java URI class, so we don't need to verify the logic there.
 * Created by navid on 1/23/20
 */
public class CRITests {

    private static final String SERVICE_NAME = "org.kinotic.tests.TestService";
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

    @Test
    public void testRawCRI1(){
        validateCRI(CRI.create(SERVICE_LITERAL1), false);
    }

    @Test
    public void testRawCRI2(){
        validateCRI(CRI.create(SERVICE_LITERAL2), true);
    }




    private void validateCRI(CRI cri, boolean checkScope){
        Validate.isTrue(cri.resourceName().equals(SERVICE_NAME), "CRI resourceName does not match expected got "+ cri.resourceName());
        Validate.isTrue(cri.version().equals(SERVICE_VERSION), "CRI version does not match expected got "+ cri.version());
        if(checkScope){
            Validate.isTrue(cri.scope().equals(SERVICE_SCOPE), "CRI scope does not match expected got "+ cri.scope());
        }
    }

}
