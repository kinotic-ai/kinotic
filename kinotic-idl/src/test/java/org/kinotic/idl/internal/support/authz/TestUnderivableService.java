package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service with a function whose name derives no permission and which declares none.
 */
@AuthzResource("widget")
public interface TestUnderivableService {

    CompletableFuture<Void> frobnicate(String id);

}
