

package org.kinotic.rpc.internal.config;

import org.kinotic.rpc.api.security.DefaultParticipant;
import org.kinotic.rpc.api.security.Participant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
public class KinoticRpcJacksonConfig {

    @Bean
    public SimpleModule kinoticRpcModule(){
        SimpleModule ret = new SimpleModule("KinoticRpcModule", Version.unknownVersion());

        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(Participant.class, DefaultParticipant.class);

        ret.setAbstractTypes(resolver);

        return ret;
    }

}
