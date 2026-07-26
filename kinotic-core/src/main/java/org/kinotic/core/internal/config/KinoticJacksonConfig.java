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
import tools.jackson.databind.cfg.ConstructorDetector;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
@Import(JacksonAutoConfiguration.class)
public class KinoticJacksonConfig {

    /**
     * Boot folds every {@code JacksonModule} bean into its managed mapper, so this bean makes the Spring
     * mapper handle the Vert.x types with their Vert.x wire format.
     */
    @Bean
    public VertxJackson3Module vertxJackson3Module() {
        return new VertxJackson3Module();
    }

    @Bean
    public JsonMapperBuilderCustomizer kinoticJsonMapperCustomizer() {
        // -parameters makes constructor parameter names visible to Jackson, which would otherwise promote
        // plain constructors to implicit creators and change how value types deserialize. EXPLICIT_ONLY
        // limits creator detection to @JsonCreator constructors, so the compiler flag cannot shift wire
        // behavior.
        return builder -> builder.constructorDetector(ConstructorDetector.EXPLICIT_ONLY);
    }

    @Bean
    public SimpleModule kinoticCoreModule(){
        SimpleModule ret = new SimpleModule("KinoticCoreModule", Version.unknownVersion());

        ret.addDeserializer(Pageable.class, new PageableDeserializer());
        ret.addSerializer(Page.class, new PageSerializer());

        return ret;
    }

    // FIXME: Make sure this works with Spring WebFlux
    // This is configured in org.kinotic.continuum.internal.api.DefaultContinuum
    // It is done there in case this bean is supplied by spring directly
    @ConditionalOnMissingBean
    @Bean
    public ReactiveAdapterRegistry reactiveAdapterRegistry(){
        return new ReactiveAdapterRegistry();
    }


}
