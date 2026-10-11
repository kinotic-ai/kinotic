package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;

import java.util.concurrent.CompletableFuture;

/**
 * A service declaring no resource whose function declares a check, which the conversion refuses.
 */
public interface TestResourcelessCheckService {

    @AuthzCheck(permission = "can_view")
    CompletableFuture<String> findById(String id);

}
