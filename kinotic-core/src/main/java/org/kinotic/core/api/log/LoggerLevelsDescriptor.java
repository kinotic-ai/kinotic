package org.kinotic.core.api.log;

/**
 * Description of levels configured for a given logger.
 */
public class LoggerLevelsDescriptor {

    private final LogLevel configuredLevel;

    public LoggerLevelsDescriptor(LogLevel configuredLevel) {
        this.configuredLevel = configuredLevel;
    }

    /**
     * @return the level set on this logger, or {@code null} when it inherits its level
     */
    public LogLevel getConfiguredLevel() {
        return this.configuredLevel;
    }

}
