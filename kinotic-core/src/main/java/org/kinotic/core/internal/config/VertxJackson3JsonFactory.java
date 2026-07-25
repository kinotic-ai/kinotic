package org.kinotic.core.internal.config;

import io.vertx.core.json.jackson.v3.DatabindCodec;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Selects the Jackson 3 databind codec for Vert.x JSON. The default {@code JacksonFactory} prefers Jackson 2
 * whenever it is on the classpath (Ignite brings it transitively), while the platform's own JSON stack is
 * Jackson 3, so this factory pins the codec explicitly.
 */
public class VertxJackson3JsonFactory implements JsonFactory {

    private static final JsonCodec CODEC = new DatabindCodec();

    @Override
    public JsonCodec codec() {
        return CODEC;
    }
}
