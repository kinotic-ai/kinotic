package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzUnchecked;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service marked unchecked as a whole, which the conversion refuses: a resource service marks its
 * functions one by one.
 */
@AuthzUnchecked
@AuthzResource("widget")
public interface TestUncheckedResourceService {

    CompletableFuture<String> findById(String id);

}
