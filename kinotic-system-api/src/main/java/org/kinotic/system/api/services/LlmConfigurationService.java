package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.system.api.model.llm.LlmConfiguration;

/**
 * The large language models the system's services call, each an {@link LlmConfiguration} that system users
 * manage and a service selects by name. A configuration's API key is given on save and held in the system's scope
 * of the secret storage until a save gives another or the configuration is deleted.
 * <p>
 * Published in the system zone, which only SYSTEM participants and the system's own cluster members may address.
 */
@Publish
public interface LlmConfigurationService {

    /** Every configuration, a page at a time. */
    Future<Page<LlmConfiguration>> findAll(Pageable pageable);

    /** The configuration {@code id}, or {@code null} when none has that id. */
    Future<LlmConfiguration> findById(String id);

    /** The configuration named {@code name}, or {@code null} when none has that name. */
    Future<LlmConfiguration> findByName(String name);

    /**
     * Saves a configuration, new or changed, and makes {@code apiKey}, when given, the key it calls its provider with.
     * Its {@code apiKeyHeld} says whether a key is held, whatever it carried.
     *
     * @param configuration the configuration, whose name no other configuration has; with an id, the existing
     *                      configuration it replaces, keeping that one's creation time
     * @param apiKey        the API key, or {@code null} or blank to keep the key held for the configuration, if any
     * @return the configuration as saved, with its id
     */
    Future<LlmConfiguration> save(LlmConfiguration configuration, String apiKey);

    /**
     * The API key held for the configuration {@code id}: the secret a system service calls the provider with. Only
     * a cluster member calling as itself, as {@code SecurityContext.callWithoutParticipant} sends a call, may read
     * it; a call made for any participant, SYSTEM participants included, is refused.
     *
     * @return the key, or {@code null} when no configuration has that id or none is held for it
     */
    Future<String> findApiKey(String id);

    /** Deletes the configuration {@code id} and the API key held for it; nothing happens for an id no configuration has. */
    Future<Void> deleteById(String id);
}
