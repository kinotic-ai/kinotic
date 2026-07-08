package org.kinotic.domain.internal.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the id rules tied to the zone grammar: {@link DomainUtil#slugifyId} always mints a
 * valid zone label, and {@link DomainUtil#validateApplicationId} enforces that grammar.
 */
class DomainUtilTest {

    @Test
    void slugifyIdNormalizesToAZoneLabel() {
        assertEquals("orders_admin", DomainUtil.slugifyId("Orders.Admin"));
        assertEquals("my_cool_app", DomainUtil.slugifyId("My Cool App"));
        assertEquals("cafe", DomainUtil.slugifyId("café"));
    }

    @Test
    void slugifyIdTrimsSeparatorsFromTheEdges() {
        assertEquals("acme_inc", DomainUtil.slugifyId("Acme Inc."));
        assertEquals("orders", DomainUtil.slugifyId(".orders."));
        assertEquals("orders", DomainUtil.slugifyId("orders.*"));
    }

    @Test
    void slugifyIdKeepsIdsThatAreAlreadyZoneLabels() {
        for (String id : List.of("orders-app", "orders_app", "1app", "a")) {
            assertEquals(id, DomainUtil.slugifyId(id));
        }
    }

    @Test
    void slugifyIdRejectsTextWithNoUsableCharacters() {
        for (String text : List.of("!!!", "...", " ", "_-_")) {
            assertThrows(IllegalArgumentException.class,
                         () -> DomainUtil.slugifyId(text),
                         "expected '" + text + "' to be rejected");
        }
    }

    @Test
    void validateApplicationIdEnforcesTheZoneLabelGrammar() {
        DomainUtil.validateApplicationId("orders-app_v2");

        for (String id : List.of("orders.app", "Orders", "orders app", "-orders", "orders-", "")) {
            assertThrows(IllegalArgumentException.class,
                         () -> DomainUtil.validateApplicationId(id),
                         "expected '" + id + "' to be rejected");
        }
        assertThrows(IllegalArgumentException.class, () -> DomainUtil.validateApplicationId(null));
    }
}
