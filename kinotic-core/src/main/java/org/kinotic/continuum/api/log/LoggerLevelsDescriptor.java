package org.kinotic.continuum.api.log;

/**
 * Description of levels configured for a given logger.
 */
public class LoggerLevelsDescriptor {

    private final LogLevel configuredLevel;

    public LoggerLevelsDescriptor(LogLevel configuredLevel) {
        this.configuredLevel = configuredLevel;
    }

    public LogLevel getConfiguredLevel() {
        return this.configuredLevel;
    }

}
