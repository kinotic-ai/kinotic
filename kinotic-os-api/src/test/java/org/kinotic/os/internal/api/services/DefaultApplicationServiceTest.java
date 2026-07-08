package org.kinotic.os.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the id guard in {@link DefaultApplicationService#beforeSave}: every write path must
 * carry an id of lowercase letters, digits, and interior dashes or underscores. The
 * collaborators are unused by beforeSave, so none are given.
 */
class DefaultApplicationServiceTest {

    private final DefaultApplicationService service = new DefaultApplicationService(null, null, null, null);

    @Test
    void beforeSaveAcceptsValidIds() {
        Application application = new Application("orders-app_v2", "desc");

        service.beforeSave(application).join();

        assertNotNull(application.getUpdated());
    }

    @Test
    void beforeSaveRejectsInvalidIds() {
        for (String id : List.of("orders.app", "Orders-App", "orders app", "orders.*")) {
            assertThrows(IllegalArgumentException.class,
                         () -> service.beforeSave(new Application(id, "desc")),
                         "expected '" + id + "' to be rejected");
        }
    }
}
