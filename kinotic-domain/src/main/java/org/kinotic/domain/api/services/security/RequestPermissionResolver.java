package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.idl.api.directory.AuthzPermissionResolver;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;

import java.util.List;

/**
 * Decides the permission a request needs for a function whose permission depends on its arguments, as a named
 * query's depends on whether its statement reads or writes. A function's check names its resolver with
 * {@code @AuthzCheck(permissionResolver = ...)}, and the {@link RequestAuthorizer} checks the resolved permission
 * on the object the check names. Every server authorizes the requests it receives for any service, so a resolver
 * is a bean of a module every server loads.
 */
public interface RequestPermissionResolver extends AuthzPermissionResolver {

    /**
     * Decides the permission one request needs.
     *
     * @param participant the caller
     * @param arguments   the request's values of {@link #parameters()}, in that order
     * @return one of {@link #possiblePermissions()}; fails with an
     *         {@link org.kinotic.core.api.exceptions.AuthorizationException} when the arguments select nothing a
     *         permission can be decided for
     */
    Future<AuthzPermissionChoice> resolve(ScopedParticipant participant, List<String> arguments);
}
