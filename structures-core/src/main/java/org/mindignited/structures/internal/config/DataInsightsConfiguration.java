package org.mindignited.structures.internal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Spring AI components used in the insights functionality.
 * 
 * This configuration sets up the ChatClient and other AI-related beans
 * needed for the insights service to function properly.
 *
 */
@Configuration
@Slf4j
@Profile("!test")
public class DataInsightsConfiguration {

    /**
     * Creates a ChatClient bean configured for insights analysis.
     * This ChatClient will be used by the AiInsightsServiceImpl to communicate
     * with the OpenAI API for data analysis and code generation.
     */
    @Bean
    public ChatClient insightsChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                         .build();
    }

}