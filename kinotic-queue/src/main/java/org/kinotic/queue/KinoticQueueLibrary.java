package org.kinotic.queue;

import org.kinotic.core.api.NodeAttribute;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.kinotic.queue.internal.cluster.ShardPlacement;
import org.kinotic.queue.internal.log.QueueLog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties
@ComponentScan
public class KinoticQueueLibrary {

    @Bean
    NodeAttribute queueNodeAttribute() {
        return new NodeAttribute(ShardPlacement.QUEUE_NODE_ATTRIBUTE, Boolean.TRUE);
    }

    @Bean
    NodeAttribute queueStorageAttribute(KinoticQueueProperties properties) {
        return new NodeAttribute(ShardPlacement.STORAGE_ID_ATTRIBUTE, QueueLog.storageId(Path.of(properties.getQueue().getDataDirectory())));
    }

}
