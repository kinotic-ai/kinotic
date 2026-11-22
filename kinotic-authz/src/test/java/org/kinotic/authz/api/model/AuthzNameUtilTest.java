package org.kinotic.authz.api.model;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.utils.AuthzConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the display name of a built-in role capitalizes each word of its id, and that an id holding
 * consecutive underscores, which the identifier grammar permits, still gets a name. Verifies too which tenant and
 * which definition each resource of an application's store names.
 */
public class AuthzNameUtilTest {

    @Test
    public void builtInRoleNameCapitalizesEachWord() {
        assertEquals("Project Editor", AuthzNameUtil.builtInRoleName("project.editor"));
        assertEquals("Vm Node Registrar", AuthzNameUtil.builtInRoleName("vm_node.registrar"));
    }

    @Test
    public void builtInRoleNameSkipsTheEmptyWordsOfConsecutiveUnderscores() {
        assertEquals("Report A B", AuthzNameUtil.builtInRoleName("report.a__b"));
        assertEquals("Report", AuthzNameUtil.builtInRoleName("report_"));
    }

    @Test
    public void aDefinitionWithinATenantNamesBoth() {
        Resource within = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzNameUtil.tenantDefinitionId("org.app.person", "acme@eu"));
        assertEquals("org.app.person", AuthzNameUtil.definitionOf(within));
        assertEquals("acme@eu", AuthzNameUtil.tenantOf(within));
    }

    @Test
    public void aTenantAndADefinitionNameThemselvesAndAnApplicationNeither() {
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, "acme");
        Resource definition = new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, "org.app.person");
        Resource application = new Resource(AuthzConstants.APPLICATION_TYPE, "org.app");
        assertEquals("acme", AuthzNameUtil.tenantOf(tenant));
        assertNull(AuthzNameUtil.definitionOf(tenant));
        assertEquals("org.app.person", AuthzNameUtil.definitionOf(definition));
        assertNull(AuthzNameUtil.tenantOf(definition));
        assertNull(AuthzNameUtil.tenantOf(application));
        assertNull(AuthzNameUtil.definitionOf(application));
    }
}
