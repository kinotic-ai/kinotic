package org.kinotic.system.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.system.api.model.log.LogLevel;
import org.kinotic.system.api.model.log.LoggerLevelsDescriptor;
import org.kinotic.system.api.model.log.LoggersDescriptor;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.List;

/**
 * Interface providing the ability to work with runtime logging configuration per node
 *
 * Created by Navid Mitchell 🤪 on 7/9/20
 */
@Publish
@Zone(DomainUtil.SYSTEM_API_ZONE)
public interface LogManager {

    @Scope
    String nodeId();

    /**
     * @return a {@link LoggersDescriptor} containing all the loggers and their levels
     */
    LoggersDescriptor loggers();

    /**
     * @param name the name of the logger to get
     * @return a {@link LoggerLevelsDescriptor} containing the logger and its levels
     */
    LoggerLevelsDescriptor loggerLevels(String name);

    /**
     * Configures the log level for the logger with the given name
     * @param name the name of the logger to set
     * @param level the {@link LogLevel} to set for the logger with the given name
     */
    void configureLogLevel(String name, LogLevel level);

    /**
     * @return the CRI patterns currently excluded from trace logging on this node
     */
    List<String> traceLogExcludes();

    /**
     * Configures the CRI patterns excluded from trace logging, silencing a service that would
     * otherwise bury the log while trace logging is on. Each pattern is matched against the fully
     * qualified CRI with Ant wildcards, so {@code srv://system-api~com.acme.HeartbeatService/*}
     * covers every method of that service and a raw CRI covers only the method it names.
     * The patterns replace whatever this node is using and last until it restarts, which returns it
     * to the {@code kinotic.traceLogExcludes} it was configured with.
     *
     * @param excludes the CRI patterns to exclude, or empty to exclude nothing
     */
    void configureTraceLogExcludes(List<String> excludes);

}
