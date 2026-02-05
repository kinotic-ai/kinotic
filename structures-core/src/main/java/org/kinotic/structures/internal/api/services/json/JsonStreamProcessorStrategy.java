package org.kinotic.structures.internal.api.services.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import java.util.List;

/**
 *
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
public interface JsonStreamProcessorStrategy<T, C, S extends JsonStreamProcessorState> {

    List<JsonTransformer> getTransformers();

    S createState(C context);

    JsonParser createParser(T input, S state);

    JsonGenerator createGenerator(T input, S state);

}
