

package org.kinotic.rpc.internal.serializer;


import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
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
