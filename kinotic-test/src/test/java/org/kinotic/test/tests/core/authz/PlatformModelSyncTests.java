package org.kinotic.test.tests.core.authz;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Verifies the platform store runs the authorization model generated from the service directory once the
 * full stack boots: the model generated from every contract the directory holds is accepted by the engine,
 * and ensuring it again keeps the version the store has rather than writing a new one.
 */
@SpringBootTest
public class PlatformModelSyncTests extends KinoticTestBase {

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private AuthzStoreService storeService;

    @Test
    public void platformStoreRunsTheModelGeneratedFromTheDirectory() throws Exception {
        List<ServiceDirectoryEntry> entries = serviceDirectory.findEntriesScopedTo(null, null, Pageable.create(0, 500, Sort.by("id")))
                                                              .await()
                                                              .getContent();
        Assertions.assertFalse(entries.isEmpty(), "the directory holds the published contracts");
        AuthzModel model = modelGenerator.platformModel(entries.stream().map(ServiceDirectoryEntry::getServiceDefinition).toList());

        String version = storeService.ensurePlatformModel(model).await();
        Assertions.assertEquals(version, storeService.ensurePlatformModel(model).await(),
                                "the platform store runs the generated model, so ensuring it writes no new version");
    }

}
