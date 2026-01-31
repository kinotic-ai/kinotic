package org.mindignited.structures.api.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.jackson.Jackson3JsonpGenerator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Class is used to represent raw json data
 * Created by Navíd Mitchell 🤪 on 5/22/23.
 */
public final class RawJson implements JsonpSerializable {

    private byte[] data;

    public RawJson() {
    }

    public RawJson(ByteBuffer buffer) {
        if(buffer.hasArray()){
            this.data = buffer.array();
        }else{
            this.data = new byte[buffer.remaining()];
            buffer.get(this.data);
        }
    }

    public RawJson(byte[] data) {
        this.data = data;
    }

    public byte[] data() {
        return data;
    }

    public ByteBuffer byteBuffer() {
        return ByteBuffer.wrap(data);
    }

    /**
     * Helper method to get a RawJson from a byte array
     * @param data the data to use
     * @return a RawJson instance
     */
    public static RawJson from(byte[] data) {
        return new RawJson(data);
    }

    /**
     * Helper method to get a RawJson from a ByteBuffer
     * @param buffer the buffer to use
     * @return a RawJson instance
     */
    public static RawJson from(ByteBuffer buffer) {
        return new RawJson(buffer);
    }

    /**
     * Helper method to get a RawJson from a JsonParser
     * @param parser the parser to get the raw json from
     * @param objectMapper the object mapper to use
     * @return a RawJson instance
     * @throws JacksonException if there is an error parsing the json
     */
    public static RawJson from(JsonParser parser,
                               ObjectMapper objectMapper) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_ARRAY
                && parser.currentToken() != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "The root of a RawJson must be an array or object", parser.currentLocation());
        }

        // Use an efficient output stream from Jackson to avoid unnecessary allocations
        try (ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()) {
            try (JsonGenerator jsonGenerator = objectMapper.createGenerator(byteArrayBuilder,
                                                                            JsonEncoding.UTF8)) {
                jsonGenerator.copyCurrentStructure(parser);
                jsonGenerator.flush();
            }
            return new RawJson(byteArrayBuilder.toByteArray());
        }
    }

    @Override
    public String toString() {
        return new String(data);
    }

    @Override
    public void serialize(jakarta.json.stream.JsonGenerator generator, JsonpMapper mapper) {
        if(generator instanceof Jackson3JsonpGenerator){
            String json = new String(data, StandardCharsets.UTF_8);
            try {
                ((Jackson3JsonpGenerator) generator).jacksonGenerator().writeRawValue(json);
            } catch (JacksonException e) {
                throw new IllegalStateException("Unable to write raw json", e);
            }
        }else{
            throw new UnsupportedOperationException("Only Jackson3JsonpGenerator is supported");
        }
    }
}
