package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Tasks;

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
    public void withMethodsReturnNewInstances() {
        Store base = Store.result("widget");
        Store wired = base.wire();

        assertFalse(base.isWire());
        assertTrue(wired.isWire());
        assertEquals(StoreType.RESULT, wired.getType());
        assertEquals("widget", wired.getName());
    }

    @Test
    public void wiringTheSharedNoneLeavesItUntouched() {
        Store wired = Store.none().wire();

        assertEquals(StoreType.NONE, wired.getType());
        assertTrue(wired.isWire());
        assertFalse(Store.none().isWire());
    }

}
