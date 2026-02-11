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

import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.ComplexC3Type;
import org.kinotic.continuum.idl.api.schema.ReferenceC3Type;
import org.springframework.core.ResolvableType;

import java.util.Set;

/**
 * Represents the current state of the conversion as well as providing a means for converters to convert dependent {@link C3Type}'s
 * <p>
 * Created by navid on 2019-06-28.
 */
public interface ConversionContext {

    /**
     * Converts the given resolvable type into a {@link C3Type} that is depended on by another {@link C3Type}
     * This will return a {@link ReferenceC3Type} when appropriate
     * @param resolvableType to convert
     * @return the {@link C3Type} representing the {@link ResolvableType}
     */
    C3Type convert(ResolvableType resolvableType);

    /**
     * @return all of the {@link ComplexC3Type} known to this {@link ConversionContext}
     */
    Set<ComplexC3Type> getComplexC3Types();

}
