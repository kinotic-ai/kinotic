package org.kinotic.test.tests.core.entity;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies how an organization's tenants are created: a tenant id is unique within its application, so creating it
 * again for the same application answers the existing tenant, at once or in turn, while another application holds a
 * tenant of the same id of its own; an id is held to the room a site label leaves in front of the application's
 * name, and a prefix of a name or an id finds that application's tenants alone.
 */
@SpringBootTest
public class TenantServiceTests extends KinoticTestBase {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    public void aPrefixFindsTheApplicationsTenantsAlone() throws Exception {
        String appId = "tenant-app-" + suffix();
        String otherAppId = "tenant-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(otherAppId, "Other application", null)));
        // one word, so the prefix below matches these two tenants and nothing another test created
        String word = "qz" + suffix().substring(0, 6);
        Tenant mine = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(appId, word + " Mine", "A customer")));
        await(runAsOrganization(() -> tenantService.createTenantIfNotExist(otherAppId, word + " Theirs", "Another customer")));
        await(tenantRepository.syncIndex());

        // the name matches whatever its case, and so does the id
        Page<Tenant> byName = await(runAsOrganization(() -> tenantService.findForApplicationByPrefix(appId, word.toUpperCase() + " M", Pageable.ofSize(10))));
        assertEquals(List.of(mine.getId()), byName.getContent().stream().map(Tenant::getId).toList());
        Page<Tenant> byId = await(runAsOrganization(() -> tenantService.findForApplicationByPrefix(appId, word, Pageable.ofSize(10))));
        assertEquals(List.of(mine.getId()), byId.getContent().stream().map(Tenant::getId).toList());
        // a blank prefix lists the application's tenants
        assertEquals(List.of(mine.getId()), await(runAsOrganization(() -> tenantService.findForApplicationByPrefix(appId, "", Pageable.ofSize(10))))
                .getContent().stream().map(Tenant::getId).toList());
    }

    @Test
    public void aTenantIdIsUniqueWithinItsApplication() throws Exception {
        String appId = "tenant-app-" + suffix();
        String otherAppId = "tenant-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(otherAppId, "Other application", null)));
        String name = "Globex " + suffix();

        Tenant globex = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(appId, name, "A customer")));
        Tenant again = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(appId, name, "Again")));
        assertEquals(globex.getId(), again.getId());
        assertEquals("A customer", again.getDescription());

        // another application's tenant of the same id is a tenant of its own
        Tenant theirs = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(otherAppId, name, "Another customer")));
        assertEquals(globex.getId(), theirs.getId());
        assertEquals("A customer", await(runAsOrganization(() -> tenantService.findById(appId, globex.getId()))).getDescription());
        assertEquals("Another customer", await(runAsOrganization(() -> tenantService.findById(otherAppId, globex.getId()))).getDescription());

        // a delete names both ids, and one of a tenant the application lacks is a no-op
        assertInstanceOf(IllegalArgumentException.class, failure(() -> tenantService.deleteById(otherAppId, "")));
        await(runAsOrganization(() -> tenantService.deleteById(otherAppId, "no-such-tenant-" + suffix())));

        await(runAsOrganization(() -> tenantService.deleteById(otherAppId, globex.getId())));
        assertNull(await(runAsOrganization(() -> tenantService.findById(otherAppId, globex.getId()))));
        assertEquals(appId, await(runAsOrganization(() -> tenantService.findById(appId, globex.getId()))).getApplicationId());
    }

    @Test
    public void creatingATenantAtOnceAnswersTheOneMadeFirst() throws Exception {
        String appId = "tenant-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        String name = "Initech " + suffix();

        // the create that loses the race finds the id taken and answers the tenant the other one made
        List<Tenant> both = await(runAsOrganization(() -> Future.all(tenantService.createTenantIfNotExist(appId, name, "First"),
                                                                     tenantService.createTenantIfNotExist(appId, name, "Second"))
                                                                .map(CompositeFuture::<Tenant>list)));
        assertEquals(both.get(0).getId(), both.get(1).getId());
        assertEquals(both.get(0).getDescription(), both.get(1).getDescription());
    }

    @Test
    public void aTenantIdIsHeldToTheRoomASiteLabelLeavesIt() throws Exception {
        String appId = "tenant-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        String longest = ("t" + suffix() + "xxxxxxxxxxxxxxxxxxxxxxxx").substring(0, HostLabelUtil.MAX_TENANT_ID_LENGTH);

        assertEquals(longest, await(runAsOrganization(() -> tenantService.createTenantIfNotExist(appId, longest, null))).getId());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> tenantService.createTenantIfNotExist(appId, longest + "x", null)));
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }
}
