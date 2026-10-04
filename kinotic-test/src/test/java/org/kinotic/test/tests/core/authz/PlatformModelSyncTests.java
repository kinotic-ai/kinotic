package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.system.api.services.WatchEventService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the platform store follows the service directory through the reconcile master: once the stack boots,
 * the store's record is reconciled to the model generated from every contract the directory holds and the engine
 * runs that model; a contract published to the directory afterwards reaches the store with no other signal; and a
 * contract published again unchanged is declined, so nothing is marked and nothing regenerates.
 */
@SpringBootTest
public class PlatformModelSyncTests extends KinoticTestBase {

    private static final String PROBE_NAMESPACE = "org.kinotic.test";
    private static final String PROBE_NAME = "PlatformModelProbeService";
    private static final String PROBE_ID = PROBE_NAMESPACE + "." + PROBE_NAME;
    private static final String PROBE_TYPE = "model_probe";

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private ServiceDirectoryStrategy directoryStrategy;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private WatchEventService watchEvents;

    @Test
    public void platformStoreRunsTheModelGeneratedFromTheDirectory() throws Exception {
        AuthzModel expected = modelFromDirectory();
        assertTrue(awaitUntil(() -> reconciledTo(expected.hash())), "the platform store never reconciled to the directory's model");

        String version = await(storeService.ensurePlatformModel(expected));
        assertEquals(version, await(storeService.ensurePlatformModel(expected)),
                     "the engine runs the generated model, so ensuring it writes no new version");
    }

    @Test
    public void aContractPublishedToTheDirectoryReachesThePlatformStore() throws Exception {
        // a permission of its own each run, so the publish changes the model whatever an earlier run left
        ServiceDirectoryEntry probe = probe("can_probe_" + System.currentTimeMillis());
        long published = contractsPublished();

        await(directoryStrategy.upsertEntry(probe));
        AuthzModel expected = modelFromDirectory();
        assertTrue(expected.permissions().containsKey(PROBE_TYPE), "the directory's model carries the published contract");
        assertTrue(awaitUntil(() -> reconciledTo(expected.hash())), "the master never carried the published contract to the platform store");
        assertTrue(awaitUntil(() -> contractsPublished() == published + 1), "the publish was never entered in the ledger");

        // the same contract again is declined in the shard operation that would have written it: no mark for
        // the master, so no ledger entry and no regeneration, which a tick and a half of the master is time to show
        await(directoryStrategy.upsertEntry(probe));
        Thread.sleep(3_000);
        assertEquals(published + 1, contractsPublished(), "an unchanged contract was entered in the ledger again");
        assertTrue(reconciledTo(expected.hash()), "the platform store left the directory's model");
    }

    private AuthzModel modelFromDirectory() throws Exception {
        List<ServiceDirectoryEntry> entries = await(serviceDirectory.findSystemEntries(Pageable.create(0, 500, Sort.by("id")))).getContent();
        return modelGenerator.platformModel(entries.stream().map(ServiceDirectoryEntry::getServiceDefinition).toList());
    }

    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.PLATFORM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }

    private long contractsPublished() throws Exception {
        return await(watchEvents.findAll(Pageable.create(0, 500, null))).getContent()
                                                                        .stream()
                                                                        .filter(event -> event.type() == WatchedType.SERVICE
                                                                                && PROBE_ID.equals(event.id())
                                                                                && event.kind() == WatchEventKind.CONTRACT_PUBLISHED)
                                                                        .count();
    }

    // A platform service with a resource type of its own, as the directory would hold it, with no handler behind it
    private static ServiceDirectoryEntry probe(String permission) {
        FunctionDefinition function = new FunctionDefinition().setName("probe");
        function.setDecorators(List.of(new AuthzCheckC3Decorator().setResource(PROBE_TYPE)
                                                                  .setObjectId("{id}")
                                                                  .setPermissionResource(PROBE_TYPE)
                                                                  .setPermission(permission)));
        ServiceDefinition definition = new ServiceDefinition().setNamespace(PROBE_NAMESPACE).setName(PROBE_NAME);
        definition.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(PROBE_TYPE).setParent("platform")));
        definition.addFunction(function);
        return new ServiceDirectoryEntry().setId(PROBE_ID)
                                          .setServiceAddress("srv://" + PROBE_NAMESPACE + "/" + PROBE_NAME)
                                          .setNamespace(PROBE_NAMESPACE)
                                          .setName(PROBE_NAME)
                                          .setZone("system")
                                          .setServiceDefinition(definition);
    }

    // The master ticks every two seconds, then generates the model and writes it to the engine

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
