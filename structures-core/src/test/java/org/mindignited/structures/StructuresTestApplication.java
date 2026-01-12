

package org.mindignited.structures;

import org.mindignited.continuum.api.annotations.EnableContinuum;
import org.mindignited.structures.api.annotations.EnableStructures;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.reactive.GraphQlWebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {HazelcastAutoConfiguration.class,
                                  JpaRepositoriesAutoConfiguration.class,
                                  GraphQlAutoConfiguration.class,
                                  GraphQlWebFluxAutoConfiguration.class,
                                  ReactiveElasticsearchClientAutoConfiguration.class,
                                  OpenAiChatAutoConfiguration.class})
@EnableContinuum
@EnableStructures
@EnableConfigurationProperties
public class StructuresTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(StructuresTestApplication.class, args);
    }
}
