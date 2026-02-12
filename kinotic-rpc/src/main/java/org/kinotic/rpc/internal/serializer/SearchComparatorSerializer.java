

package org.kinotic.rpc.internal.serializer;

import org.kinotic.rpc.api.crud.SearchComparator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Created by Navíd Mitchell 🤪 on 7/30/21.
 */
public class SearchComparatorSerializer extends ValueSerializer<SearchComparator> {

    @Override
    public void serialize(SearchComparator value,
                          JsonGenerator gen,
                          SerializationContext ctxt) throws JacksonException {
        gen.writeString(value.getStringValue());
    }

}
