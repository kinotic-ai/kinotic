package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The authorization models the service directory implies, as it holds the services now.
 */
public interface AuthzModelService {

    /**
     * The event bus address the directory announces on that contracts deciding resource types of an application
     * were written. The body is a JSON object: {@code store}, the application's store, and {@code types}, the
     * types whose permissions or roles may have changed.
     */
    String TYPES_CHANGED_ADDRESS = "kinotic.authz.types-changed";

    /**
     * The model of the system's store, from the system's own services.
     *
     * @return the model
     */
    Future<AuthzModel> findSystemModel();

    /**
     * The model of the system's store, once the store runs it with every one of {@code roleIds} in it. The
     * services a server publishes reach the directory, and the model they imply reaches the store, shortly after
     * the server starts. The future fails with an {@link IllegalStateException} when the store runs no such model
     * within five minutes.
     *
     * @param roleIds the roles the model must have
     * @return the model
     */
    Future<AuthzModel> awaitSystemModel(Collection<String> roleIds);

    /**
     * The model of an application's store with everything the application's services declare, from the system's
     * services its users call and every service its runtimes registered. It reads every contract of the
     * application, so it is for listing the application's whole catalog; a question about some of its types or
     * permissions is answered by {@link #findApplicationModelOf}.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @return the model
     */
    Future<AuthzModel> findApplicationModel(String organizationId, String applicationId);

    /**
     * The model of an application's store as far as the given types and permissions go. What the store's engine
     * runs, and the roles homed at the types the store places, are whole in it. Of the application's own services it
     * has the types given, the types the given permissions belong to, and every type one of those is decided
     * together with: its parent, the types inside it, and any type a service of it also names. Their permissions
     * and roles are as {@link #findApplicationModel(String, String)} has them; a type outside them is absent.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @param types          the resource types the model must cover; none for the store's own types alone
     * @param permissions    the model names of the permissions the model must cover
     * @return the model
     */
    Future<AuthzModel> findApplicationModelOf(String organizationId,
                                              String applicationId,
                                              Collection<String> types,
                                              Collection<String> permissions);

    /**
     * The model of an application's store as far as the types the store places go: what the store's engine runs
     * and the roles homed at those types, without the types of the application's own services.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @return the model
     */
    Future<AuthzModel> findApplicationStoreModel(String organizationId, String applicationId);

    /**
     * The model of an application's store as far as the home type of a role goes, as
     * {@link #findApplicationModelOf} has it for that type. The future fails with an
     * {@link IllegalArgumentException} for an id that names no home type.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @param roleId         the role, {@code <home type>.<name>}
     * @return the model
     */
    Future<AuthzModel> findApplicationModelOfRole(String organizationId, String applicationId, String roleId);

    /**
     * Writes contracts to the directory with what follows from them for authorization. An application's contracts
     * are written only once a model can be generated from them and the services deciding the same types, so a
     * registration fails with the reason instead of leaving the application with declarations no model can be made
     * of. Once written, a contract that decides a type its application's store places renews the intent of that
     * store, and a system contract the intent of every store, so the store's model is generated again from the
     * directory; and the types an application's contracts decide are announced on {@link #TYPES_CHANGED_ADDRESS}.
     *
     * @param entries the entries about to be written
     * @param write   writes the entries, answering the ids of those whose contract changed
     * @return a future emitting what the write answers, or failing with an {@link IllegalArgumentException},
     *         nothing written, when no model can be generated from an application's contracts
     */
    Future<Set<String>> writeContracts(Collection<ServiceDirectoryEntry> entries, Supplier<Future<Set<String>>> write);

}
