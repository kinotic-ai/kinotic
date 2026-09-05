

package org.kinotic.domain.internal.config;

import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.internal.serializer.RawJsonDeserializer;
import org.kinotic.domain.internal.serializer.RawJsonSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.Version;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
public class KinoticDomainJacksonConfig {

    @Bean
    public SimpleModule kinoticDomainModule(){
        SimpleModule ret = new SimpleModule("KinoticDomainModule", Version.unknownVersion());

        ret.addDeserializer(RawJson.class, new RawJsonDeserializer(new ObjectMapper()));
        ret.addSerializer(RawJson.class, new RawJsonSerializer());

        ret.setMixInAnnotation(Participant.class, ParticipantMixin.class);
        ret.registerSubtypes(DefaultSystemParticipant.class,
                             DefaultOrganizationParticipant.class,
                             DefaultApplicationParticipant.class);

        return ret;
    }

}
