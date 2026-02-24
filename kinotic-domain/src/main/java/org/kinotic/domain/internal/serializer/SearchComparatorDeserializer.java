package org.kinotic.domain.internal.serializer;


import org.kinotic.domain.api.services.crud.SearchComparator;
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
