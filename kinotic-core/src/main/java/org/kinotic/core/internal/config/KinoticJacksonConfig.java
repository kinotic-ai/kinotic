package org.kinotic.core.internal.config;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.internal.serializer.PageSerializer;
import org.kinotic.core.internal.serializer.PageableDeserializer;
import org.kinotic.vertx.jackson3.VertxJackson3Module;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.core.Version;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
@Import(JacksonAutoConfiguration.class)
public class KinoticJacksonConfig {

    /**
     * Applies the platform's conversion to a mapper builder: the Vert.x types in their Vert.x wire format, the
     * platform's own value types, and no constructor parameter detection. The Boot-managed mapper and the mapper a
     * process without a Spring context converts with are both built from it, so both convert alike.
     * @param builder the builder to configure
     * @return the builder
     */
    public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
        // -parameters makes constructor parameter names visible to Jackson, which promotes plain
        // constructors (@AllArgsConstructor) to properties-based creators even under
        // ConstructorDetector.EXPLICIT_ONLY: a creator invocation passes null for absent properties,
        // bypassing field initializers (TestFunctionDefinitionSerialization documents the failure).
        // Disabling detection makes Jackson ignore reflected parameter names entirely.
        return builder.addModule(new VertxJackson3Module())
                      .addModule(kinoticCoreModule())
                      .disable(MapperFeature.DETECT_PARAMETER_NAMES);
    }

    @Bean
    public JsonMapperBuilderCustomizer kinoticJsonMapperCustomizer() {
        return KinoticJacksonConfig::configure;
    }

    /**
     * The serializers of the platform's own value types, {@link Page} and {@link Pageable}.
     */
    private static SimpleModule kinoticCoreModule(){
        SimpleModule ret = new SimpleModule("KinoticCoreModule", Version.unknownVersion());

        ret.addDeserializer(Pageable.class, new PageableDeserializer());
        ret.addSerializer(Page.class, new PageSerializer());

        return ret;
    }

    // FIXME: Make sure this works with Spring WebFlux
    // This is configured in org.kinotic.core.internal.DefaultKinotic
    // It is done there in case this bean is supplied by spring directly
    @ConditionalOnMissingBean
    @Bean
    public ReactiveAdapterRegistry reactiveAdapterRegistry(){
        return new ReactiveAdapterRegistry();
    }


}
