package org.kinotic.continuum.api.log;

import org.springframework.boot.logging.LoggerConfiguration;

/**
 * Created by Navíd Mitchell 🤪 on 4/5/23.
 */
public
class SingleLoggerLevelsDescriptor extends LoggerLevelsDescriptor {

    private final LogLevel effectiveLevel;

    public SingleLoggerLevelsDescriptor(LoggerConfiguration configuration) {
        super(LogLevel.fromString(configuration.getConfiguredLevel().name()));
        this.effectiveLevel = LogLevel.fromString(configuration.getEffectiveLevel().name());
    }

    public LogLevel getEffectiveLevel() {
        return this.effectiveLevel;
    }

}
