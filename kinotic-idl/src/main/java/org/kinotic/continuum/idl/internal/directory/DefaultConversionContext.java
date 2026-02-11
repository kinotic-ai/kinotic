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

import org.apache.commons.lang3.StringUtils;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.ComplexC3Type;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.continuum.idl.api.schema.ReferenceC3Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;

import java.util.*;

/**
 * Default implementation of {@link ConversionContext}
 * Created by navid on 2019-07-01.
 */
public class DefaultConversionContext implements ConversionContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultConversionContext.class);

    private final ResolvableTypeConverter resolvableTypeConverter;

    private final Deque<ResolvableType> circularReferenceCheckStack = new ArrayDeque<>();

    private final Deque<ResolvableType> errorStack = new ArrayDeque<>();

    private final Map<String, C3Type> schemaCache = new HashMap<>();

    private final Set<ComplexC3Type> complexC3Types = new HashSet<>();

    private final boolean shouldCreateReferences;

    /**
     * Creates a new {@link ConversionContext}
     * @param resolvableTypeConverter the converter to use to be used for conversion. Typically, this will be a {@link ResolvableTypeConverterComposite}
     * @param shouldCreateReferences if true, {@link ObjectC3Type}'s will be added to the {@link #getComplexC3Types()} set and {@link ReferenceC3Type}'s will be returned when appropriate
     */
    public DefaultConversionContext(ResolvableTypeConverter resolvableTypeConverter,
                                    boolean shouldCreateReferences) {
        this.resolvableTypeConverter = resolvableTypeConverter;
        this.shouldCreateReferences = shouldCreateReferences;
    }

    public C3Type convertInternal(ResolvableType resolvableType) {

        if(circularReferenceCheckStack.contains(resolvableType)){
            IllegalStateException ise = new IllegalStateException("Circular reference detected for "+resolvableType);
            logException(ise);
            throw ise;
        }
        C3Type ret;
        try {

            circularReferenceCheckStack.addFirst(resolvableType);

            // FIXME: verify this cache logic!
            // Since decorators and metadata could differ on the final C3Type we need to make sure they don't share a java reference when they shouldn't
            String key = resolvableType.toString();
            if (schemaCache.containsKey(key)) {
                ret = schemaCache.get(key);
            } else {
                ret = resolvableTypeConverter.convert(resolvableType, this);
                // We only cache object types
                if(ret instanceof ObjectC3Type){
                    schemaCache.put(key, ret);
                }
            }

        } catch (Exception e){
            logException(e);
            throw e;
        } finally {
            circularReferenceCheckStack.removeFirst();
        }
        return ret;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType) {
        C3Type c3Type = convertInternal(resolvableType);
        if(c3Type instanceof ObjectC3Type && shouldCreateReferences){
            ObjectC3Type objectC3Type = (ObjectC3Type) c3Type;
            complexC3Types.add(objectC3Type);
            c3Type = new ReferenceC3Type(objectC3Type.getQualifiedName());
        }
        return c3Type;
    }

    @Override
    public Set<ComplexC3Type> getComplexC3Types() {
        return complexC3Types;
    }

    /**
     * Log an exception when appropriate dealing with only logging once even when recursion has occurred
     * @param e to log
     */
    private void logException(Exception e){
        if(log.isDebugEnabled() || log.isTraceEnabled()){
            // This indicates this is the first time logException has been called for this context.
            // This would occur at the furthest call depth so at this point the circularReferenceCheckStack has the complete stack
            if(errorStack.isEmpty()){
                // We loop vs add all to keep stack intact
                for(ResolvableType resolvableType: circularReferenceCheckStack){
                    errorStack.addFirst(resolvableType);
                }
            }
            if(circularReferenceCheckStack.size() == 1) { // we are at the top of the stack during recursion
                StringBuilder sb = new StringBuilder("Error occurred during conversion.\n" + e.getMessage() + "\n");
                int objectCount = 1;
                for (ResolvableType resolvableType : errorStack) {
                    sb.append(StringUtils.leftPad("", objectCount, '\t'));
                    sb.append("- ");
                    sb.append(resolvableType.toString());
                    sb.append("\n");
                    objectCount++;
                }
                log.debug(sb.toString());
                errorStack.clear(); // we have printed reset
            }
        }
    }

}
