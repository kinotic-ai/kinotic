package org.kinotic.idl.api.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the display name of a built-in role capitalizes each word of its id, and that an id holding
 * consecutive underscores, which the identifier grammar permits, still gets a name.
 */
public class AuthzUtilTest {

    @Test
    public void builtInRoleNameCapitalizesEachWord() {
        assertEquals("Project Editor", AuthzUtil.builtInRoleName("project.editor"));
        assertEquals("Vm Node Registrar", AuthzUtil.builtInRoleName("vm_node.registrar"));
    }

    @Test
    public void builtInRoleNameSkipsTheEmptyWordsOfConsecutiveUnderscores() {
        assertEquals("Report A B", AuthzUtil.builtInRoleName("report.a__b"));
        assertEquals("Report", AuthzUtil.builtInRoleName("report_"));
    }
}
