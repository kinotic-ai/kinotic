/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.api;

import org.kinotic.continuum.api.*;
import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.api.log.*;
import org.kinotic.continuum.api.log.LogLevel;
import org.springframework.boot.logging.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default impl of {@link LogManager}
 *
 * Shamelessly copied from org.springframework.boot.actuate.logging.LoggersEndpoint
 *
 * @author Ben Hale
 * @author Phillip Webb
 * @author HaiTao Zhang
 */
@Component
public class DefaultLogManager implements LogManager {


    private final Continuum continuum;
    private final LoggingSystem loggingSystem;
    private final LoggerGroups loggerGroups;


    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public DefaultLogManager(Continuum continuum,
                             LoggingSystem loggingSystem,
                             LoggerGroups loggerGroups) {
        this.continuum = continuum;
        this.loggingSystem = loggingSystem;
        this.loggerGroups = loggerGroups;
    }

    @Override
    public String nodeId() {
        return continuum.serverInfo().getNodeId();
    }

    public LoggersDescriptor loggers() {
        Collection<LoggerConfiguration> configurations = this.loggingSystem.getLoggerConfigurations();
        if (configurations == null) {
            return new LoggersDescriptor();
        }
        return new LoggersDescriptor(getLevels(), getLoggers(configurations), getGroups());
    }

    private Map<String, GroupLoggerLevelsDescriptor> getGroups() {
        Map<String, GroupLoggerLevelsDescriptor> groups = new LinkedHashMap<>();
        this.loggerGroups.forEach((group) -> groups.put(group.getName(),
                                                        new GroupLoggerLevelsDescriptor(LogLevel.fromString(group.getConfiguredLevel().name()), group.getMembers())));
        return groups;
    }

    public LoggerLevelsDescriptor loggerLevels(String name) {
        Validate.notNull(name, "Name must not be null");
        LoggerGroup group = this.loggerGroups.get(name);
        if (group != null) {
            return new GroupLoggerLevelsDescriptor(LogLevel.fromString(group.getConfiguredLevel().name()), group.getMembers());
        }
        LoggerConfiguration configuration = this.loggingSystem.getLoggerConfiguration(name);
        return (configuration != null) ? new SingleLoggerLevelsDescriptor(configuration) : null;
    }

    public void configureLogLevel(String name, LogLevel configuredLevel) {
        Validate.notBlank(name, "Name must not be blank");
        Validate.notNull(name, "ConfiguredLevel must not be null");

        LoggerGroup group = this.loggerGroups.get(name);
        org.springframework.boot.logging.LogLevel bootLevel = org.springframework.boot.logging.LogLevel.valueOf(configuredLevel.name());
        if (group != null && group.hasMembers()) {
            group.configureLogLevel(bootLevel, this.loggingSystem::setLogLevel);
            return;
        }
        this.loggingSystem.setLogLevel(name, bootLevel);
    }

    private NavigableSet<LogLevel> getLevels() {
        Set<LogLevel> levels = this.loggingSystem.getSupportedLogLevels()
                                                 .stream()
                                                 .map(logLevel -> LogLevel.fromString(logLevel.name())).collect(Collectors.toSet());
        return new TreeSet<>(levels).descendingSet();
    }

    private Map<String, SingleLoggerLevelsDescriptor> getLoggers(Collection<LoggerConfiguration> configurations) {
        Map<String, SingleLoggerLevelsDescriptor> loggers = new LinkedHashMap<>(configurations.size());
        for (LoggerConfiguration configuration : configurations) {
            loggers.put(configuration.getName(), new SingleLoggerLevelsDescriptor(configuration));
        }
        return loggers;
    }

}
