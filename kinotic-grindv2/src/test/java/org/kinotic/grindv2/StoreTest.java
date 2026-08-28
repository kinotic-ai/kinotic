package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.Store;
import org.kinotic.grindv2.api.model.StoreType;
import org.kinotic.grindv2.api.model.Tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link Store} guards: combinations that contradict the store's semantics are rejected
 * at construction, and the with-style methods leave the original instance untouched.
 */
public class StoreTest {

    @Test
    public void stateCannotDeclareAReload() {
        assertThrows(IllegalStateException.class,
                     () -> Store.state("decision").reload(Tasks.noop("reload")));
    }

    @Test
    public void aStoreKeepingNothingCannotPublish() {
        assertThrows(IllegalStateException.class, () -> Store.none().wire());
    }

    @Test
    public void withMethodsReturnNewInstances() {
        Store base = Store.result("widget");
        Store wired = base.wire();

        assertFalse(base.isWire());
        assertTrue(wired.isWire());
        assertEquals(StoreType.RESULT, wired.getType());
        assertEquals("widget", wired.getName());
    }

}
