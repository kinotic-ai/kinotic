package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.authz.api.services.AuthzStoreService;

/**
 * The authorization store of one scope: the platform's, which every platform service's contract belongs to, or
 * an application's. The model a store should run is the one generated from the directory entries that belong
 * to it, so the record is reconciled when the engine runs that model and falls out of its desired state when an
 * entry of the store is published. One row per store; the platform's row exists before any server does, as the
 * platform's store in the engine does.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class AuthzStore implements Reconcilable<AuthzModelRevision> {
    /**
     * The id of the platform's store record.
     */
    public static final String PLATFORM = AuthzStoreService.PLATFORM;

    /**
     * {@link #PLATFORM} for the platform's store, or the application's id in the stores,
     * {@link org.kinotic.domain.api.utils.DomainUtil#authzApplicationId}.
     */
    private String id;
    /**
     * The owning organization, or null for the platform's store.
     */
    private String organizationId;
    /**
     * The owning application, or null for the platform's store.
     */
    private String applicationId;
    /**
     * The model the store should run, the one the directory implies, beside the model the engine runs.
     */
    private ReconcileState<AuthzModelRevision> state = new ReconcileState<>();
}
