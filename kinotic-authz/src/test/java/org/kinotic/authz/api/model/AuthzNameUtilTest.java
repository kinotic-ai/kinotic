package org.kinotic.authz.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the display name of a built-in role capitalizes each word of its id, and that an id holding
 * consecutive underscores, which the identifier grammar permits, still gets a name.
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
}
