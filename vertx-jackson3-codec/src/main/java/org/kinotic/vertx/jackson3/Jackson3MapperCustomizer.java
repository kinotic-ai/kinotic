package org.kinotic.vertx.jackson3;

import tools.jackson.databind.json.JsonMapper;

/**
 * Customizes the {@link JsonMapper} backing the {@link VertxJackson3Codec}. Implementations are discovered
 * with {@link java.util.ServiceLoader}, so registering one in
 * {@code META-INF/services/org.kinotic.vertx.jackson3.Jackson3MapperCustomizer} is enough to configure the
 * mapper Vert.x uses for JSON — modules, features, constructor detection, stream constraints, and anything
 * else the builder exposes.
 *
 * <p>Customizers run once, when the codec first loads. The order between multiple registered customizers is
 * unspecified, so independent customizers must not contradict each other.
 */
public interface Jackson3MapperCustomizer {

    /**
     * Applies this customization to the builder the codec's mapper is built from.
     *
     * @param builder the builder, already carrying the {@link VertxJackson3Module}
     */
    void customize(JsonMapper.Builder builder);

}
