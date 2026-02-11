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

package org.kinotic.rpc.internal.serializer;


import org.kinotic.continuum.core.api.crud.SearchComparator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Created by Navíd Mitchell 🤪 on 7/30/21.
 */
public class SearchComparatorDeserializer extends ValueDeserializer<SearchComparator> {

    @Override
    public SearchComparator deserialize(JsonParser jsonParser,
                                        DeserializationContext ctxt) throws JacksonException {

        JsonNode node = jsonParser.objectReadContext().readTree(jsonParser);

        return SearchComparator.fromStringValue(node.stringValue());
    }
}
