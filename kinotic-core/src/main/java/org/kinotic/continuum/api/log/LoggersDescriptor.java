package org.kinotic.continuum.api.log;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Description of loggers.
 */
public class LoggersDescriptor {

    private final Set<LogLevel> levels;

    // Map of log configuration name and value of LoggerLevels
    private final Map<String, SingleLoggerLevelsDescriptor> loggerLevels;

    private final Map<String, GroupLoggerLevelsDescriptor> groups;

    public LoggersDescriptor() {
        this.levels = Collections.emptySet();
        this.loggerLevels = Collections.emptyMap();
        this.groups = Collections.emptyMap();
    }

    public LoggersDescriptor(Set<LogLevel> levels,
                             Map<String, SingleLoggerLevelsDescriptor> loggerLevels,
                             Map<String, GroupLoggerLevelsDescriptor> groups) {
        this.levels = levels;
        this.loggerLevels = loggerLevels;
        this.groups = groups;
    }

    /**
     * @return Set of available log levels
     */
    public Set<LogLevel> getLevels() {
        return this.levels;
    }

    /**
     * @return Map of log configuration name and value of SingleLoggerLevels
     */
    public Map<String, SingleLoggerLevelsDescriptor> getLoggerLevels() {
        return this.loggerLevels;
    }

    /**
     * @return Map of group name and GroupLoggerLevels
     */
    public Map<String, GroupLoggerLevelsDescriptor> getGroups() {
        return this.groups;
    }
}
