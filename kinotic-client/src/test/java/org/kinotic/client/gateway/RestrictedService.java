package org.kinotic.client.gateway;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;

import java.util.concurrent.CompletableFuture;

/**
 * A service in a zone the test participant may not send to.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Publish
@Version("1.0.0")
@Zone(TestGatewayApplication.RESTRICTED_ZONE)
public interface RestrictedService {

    CompletableFuture<String> secret();

}
