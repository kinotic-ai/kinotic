package org.kinotic.system.api.model.llm;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A large language model the system's services call: the provider, the model, where the provider's API is
 * reached and the options each request carries. The API key the provider is called with is held in the secret
 * storage and is never a field of the configuration; {@link #apiKeyHeld} says whether one is held.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LlmConfiguration implements Identifiable<String> {

    private String id;

    /** The name a service selects the configuration by, unique across the system. */
    private String name;

    private LlmProviderType provider;

    /** The provider's id of the model, such as {@code claude-opus-5-5}. */
    private String model;

    /** The base URL of the provider's API, or {@code null} for the provider's public API. */
    private String endpoint;

    /** The most tokens a response may hold, or {@code null} for the calling service's default. */
    private Integer maxTokens;

    /**
     * The sampling temperature, from 0 to the provider's {@link LlmProviderType#getMaxTemperature()}, or {@code null}
     * for the provider's default.
     */
    private Double temperature;

    /** Whether an API key is held for the configuration. */
    private boolean apiKeyHeld;

    private Date created;

    private Date updated;
}
