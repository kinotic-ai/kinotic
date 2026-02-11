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

package org.kinotic.continuum.grind.api;

import org.kinotic.continuum.grind.internal.api.GrindConstants;
import org.apache.commons.lang3.Validate;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 *
 * Created by Navid Mitchell on 7/7/20
 */
public class ContextUtils {

    /**
     * Gets a property from the given active context.
     * @param propertyName the name of the property to retrieve
     * @param <T> type of the property expected
     * @return the property or null if not value is found for the given name
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProperty(String propertyName, GenericApplicationContext applicationContext){
        Validate.notBlank(propertyName, "propertyName must not be blank");
        MapPropertySource propertySource = getGrindPropertySource(applicationContext);

        return (T) propertySource.getProperty(propertyName);
    }

    /**
     * Returns the {@link MapPropertySource} used internally by "Grind" to store property values during {@link Task} execution
     * @param applicationContext to get the {@link MapPropertySource} from
     * @return the {@link MapPropertySource}
     */
    public static MapPropertySource getGrindPropertySource(GenericApplicationContext applicationContext){
        Validate.notNull(applicationContext,"applicationContext must not be null");

        MapPropertySource propertySource = (MapPropertySource) applicationContext.getEnvironment()
                                                                                 .getPropertySources()
                                                                                 .get(GrindConstants.GRIND_MAP_PROPERTY_SOURCE);
        Validate.notNull(propertySource, "Could not find Grind property source");

        return propertySource;
    }

}
