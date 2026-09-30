package org.kinotic.core.api.log;

import org.springframework.boot.logging.LoggerConfiguration;

/**
 * Created by Navíd Mitchell 🤪 on 4/5/23.
 */
public
class SingleLoggerLevelsDescriptor extends LoggerLevelsDescriptor {

    private final LogLevel effectiveLevel;

    public SingleLoggerLevelsDescriptor(LoggerConfiguration configuration) {
        super(LogLevel.of(configuration.getConfiguredLevel()));
        this.effectiveLevel = LogLevel.of(configuration.getEffectiveLevel());
    }

    public LogLevel getEffectiveLevel() {
        return this.effectiveLevel;
    }

}
