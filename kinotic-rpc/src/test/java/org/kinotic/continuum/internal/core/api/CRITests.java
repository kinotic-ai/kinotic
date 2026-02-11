/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.core.api;

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.EventConstants;
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
