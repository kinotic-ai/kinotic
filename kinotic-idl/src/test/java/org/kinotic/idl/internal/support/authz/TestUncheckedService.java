package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzUnchecked;

import java.util.concurrent.CompletableFuture;

/**
 * A service declaring no resource, marked unchecked as a whole: every function answers for the caller alone.
 */
@AuthzUnchecked
public interface TestUncheckedService {

    CompletableFuture<String> findMyProfile();

    CompletableFuture<Void> updateDisplayName(String displayName);

}
