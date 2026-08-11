package org.kinotic.billing.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.billing.api.model.LedgerEntryType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LedgerEntryIdsTest {

    @Test
    public void whenSameObjectAndType_thenIdIsDeterministic() {
        assertEquals(LedgerEntryIds.entryId("ch_123", LedgerEntryType.EARNING),
                     LedgerEntryIds.entryId("ch_123", LedgerEntryType.EARNING));
    }

    @Test
    public void whenEntryTypeDiffers_thenIdDiffers() {
        assertNotEquals(LedgerEntryIds.entryId("ch_123", LedgerEntryType.EARNING),
                        LedgerEntryIds.entryId("ch_123", LedgerEntryType.REFUND_CLAWBACK));
    }

    @Test
    public void whenComposed_thenFormatIsObjectIdColonTypeName() {
        assertEquals("ch_123:EARNING", LedgerEntryIds.entryId("ch_123", LedgerEntryType.EARNING));
    }

    @Test
    public void whenObjectIdBlank_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                     () -> LedgerEntryIds.entryId(" ", LedgerEntryType.EARNING));
    }
}
