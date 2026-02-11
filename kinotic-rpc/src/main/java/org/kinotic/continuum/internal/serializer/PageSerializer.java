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

package org.kinotic.continuum.internal.serializer;


import org.kinotic.continuum.core.api.crud.CursorPage;
import org.kinotic.continuum.core.api.crud.Page;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 *
 * Created by navid on 2/4/20
 */
@SuppressWarnings("rawtypes")
public class PageSerializer extends ValueSerializer<Page> {

    @Override
    public void serialize(Page page,
                          JsonGenerator jsonGenerator,
                          SerializationContext ctxt) throws JacksonException {
        jsonGenerator.writeStartObject();

        if(page.getTotalElements() != null){
            jsonGenerator.writeNumberProperty("totalElements", page.getTotalElements());
        }else{
            jsonGenerator.writeNullProperty("totalElements");
        }

        jsonGenerator.writeArrayPropertyStart("content");
        for (Object value: page.getContent()) {
            jsonGenerator.writePOJO(value);
        }
        jsonGenerator.writeEndArray();

        if(page instanceof CursorPage) {
            jsonGenerator.writeStringProperty("cursor", ((CursorPage) page).getCursor());
        }

        jsonGenerator.writeEndObject();
    }

}
