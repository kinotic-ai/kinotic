

package org.mindignited.structures;

import org.mindignited.continuum.api.annotations.EnableContinuum;
import org.mindignited.structures.api.annotations.EnableStructures;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {OpenAiChatAutoConfiguration.class})
@EnableContinuum
@EnableStructures
@EnableConfigurationProperties
public class StructuresTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(StructuresTestApplication.class, args);
    }
}
