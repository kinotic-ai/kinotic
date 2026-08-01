package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Publish;

import java.util.concurrent.CompletableFuture;

/**
 * Contract whose implementation renames the parameter, so the contract and the implementation are
 * distinguishable naming sources. Java does not require an override to reuse the declared parameter names,
 * so any service may look like this.
 *
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@Publish
public interface RenamedParameterService {

    CompletableFuture<String> echo(String contractName);

}
