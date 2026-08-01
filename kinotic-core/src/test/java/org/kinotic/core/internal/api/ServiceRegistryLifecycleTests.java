package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.internal.api.support.DefaultRpcTestService;
import org.kinotic.core.internal.api.support.RpcTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Covers the registry's identifier lifecycle: which registrations hold an identifier and which release it.
 * The registry keys a live event bus consumer by {@link ServiceIdentifier}, so an identifier released while
 * its consumer is still listening lets a later registration put a second supervisor on one address, and an
 * identifier held by a registration that never took effect blocks every retry.
 *
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class ServiceRegistryLifecycleTests {

    @Autowired
    private ServiceRegistry serviceRegistry;
    @Autowired
    private DefaultRpcTestService rpcTestService;

    @Test
    public void unregisterReleasesTheIdentifierForReRegistration() throws Exception {
        ServiceIdentifier serviceIdentifier = probeIdentifier("RegistryLifecycleProbe");

        await(serviceRegistry.register(serviceIdentifier, RpcTestService.class, rpcTestService));
        await(serviceRegistry.unregister(serviceIdentifier));

        // the identifier is released as part of a successful stop, so the same one registers again
        await(serviceRegistry.register(serviceIdentifier, RpcTestService.class, rpcTestService));
        await(serviceRegistry.unregister(serviceIdentifier));
    }

    @Test
    public void rejectedDuplicateRegistrationLeavesTheServiceRegistered() throws Exception {
        ServiceIdentifier serviceIdentifier = probeIdentifier("RegistryDuplicateProbe");

        await(serviceRegistry.register(serviceIdentifier, RpcTestService.class, rpcTestService));
        try {
            Assertions.assertThrows(ExecutionException.class,
                                    () -> await(serviceRegistry.register(serviceIdentifier,
                                                                         RpcTestService.class,
                                                                         rpcTestService)));
        } finally {
            // the rejected duplicate must not have released the identifier the first registration holds
            await(serviceRegistry.unregister(serviceIdentifier));
        }
    }

    @Test
    public void unregisteringAnIdentifierThatIsNotRegisteredFails() {
        Assertions.assertThrows(ExecutionException.class,
                                () -> await(serviceRegistry.unregister(probeIdentifier("RegistryAbsentProbe"))));
    }

    private ServiceIdentifier probeIdentifier(String name) {
        return new ServiceIdentifier(null, "org.kinotic.tests", name, null, "1.0.0");
    }

    private void await(Future<Void> future) throws Exception {
        future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

}
