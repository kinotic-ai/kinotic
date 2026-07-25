package org.kinotic.core.internal.config;

import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Selects the Jackson 3 databind codec for Vert.x JSON. The default {@code JacksonFactory} prefers Jackson 2
 * whenever it is on the classpath (Ignite brings it transitively), while the platform's own JSON stack is
 * Jackson 3, so this factory pins the codec explicitly.
 */
public class VertxJackson3JsonFactory implements JsonFactory {

    private static final JsonCodec CODEC = createJackson3Codec();

    @Override
    public JsonCodec codec() {
        return CODEC;
    }

    // the codec class exists only in vertx-core's META-INF/versions/21 section, which source-processing
    // tools (delombok, javadoc) cannot resolve; the runtime class loader handles multi-release jars
    // normally, so it is loaded reflectively and a failure here fails the JsonFactory SPI load at boot
    private static JsonCodec createJackson3Codec() {
        try {
            return (JsonCodec) Class.forName("io.vertx.core.json.jackson.v3.DatabindCodec")
                                    .getDeclaredConstructor()
                                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The vertx Jackson 3 codec could not be loaded", e);
        }
    }
}
