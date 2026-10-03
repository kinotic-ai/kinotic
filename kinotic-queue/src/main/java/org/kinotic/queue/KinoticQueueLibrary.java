package org.kinotic.queue;

import org.kinotic.core.api.NodeAttribute;
import org.kinotic.queue.internal.cluster.ShardPlacement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ComponentScan
public class KinoticQueueLibrary {

    @Bean
    NodeAttribute queueNodeAttribute() {
        return new NodeAttribute(ShardPlacement.QUEUE_NODE_ATTRIBUTE, Boolean.TRUE);
    }

}
