package org.kinotic.core.api.log;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LoggerConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SingleLoggerLevelsDescriptorTest {

    @Test
    void aLoggerThatInheritsItsLevelHasNoConfiguredLevel() {
        SingleLoggerLevelsDescriptor descriptor = new SingleLoggerLevelsDescriptor(
                new LoggerConfiguration("org.example", null, org.springframework.boot.logging.LogLevel.INFO));

        assertNull(descriptor.getConfiguredLevel());
        assertEquals(LogLevel.INFO, descriptor.getEffectiveLevel());
    }

    @Test
    void aConfiguredLoggerCarriesBothLevels() {
        SingleLoggerLevelsDescriptor descriptor = new SingleLoggerLevelsDescriptor(
                new LoggerConfiguration("org.example",
                                        org.springframework.boot.logging.LogLevel.DEBUG,
                                        org.springframework.boot.logging.LogLevel.DEBUG));

        assertEquals(LogLevel.DEBUG, descriptor.getConfiguredLevel());
        assertEquals(LogLevel.DEBUG, descriptor.getEffectiveLevel());
    }
}
