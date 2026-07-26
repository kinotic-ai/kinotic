package org.kinotic.vertx.jackson3;

import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Selects the {@link VertxJackson3Codec} for Vert.x JSON. Registered via {@code META-INF/services}, so adding
 * this module to the classpath is enough: any registered {@link JsonFactory} takes precedence over the codec
 * Vert.x falls back to on its own.
 */
public class VertxJackson3JsonFactory implements JsonFactory {

    private static final JsonCodec CODEC = new VertxJackson3Codec();

    @Override
    public JsonCodec codec() {
        return CODEC;
    }

}
