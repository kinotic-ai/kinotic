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

package org.kinotic.continuum.idl.internal.directory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default converter which can be wired by spring. This is only expected to be used by implementors.
 * <p>
 * Created by navid on 2019-06-13.
 */
@Primary
@Component
public class DefaultResolvableTypeConverter extends ResolvableTypeConverterComposite {

    public DefaultResolvableTypeConverter(List<ResolvableTypeConverter> autowiredConverters) {
        addConverters(autowiredConverters);

        // This is added manually since we want it to always be used last and @Order annotation was not working properly
        addConverter(new PojoTypeConverter());
    }

}
