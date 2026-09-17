export enum LogLevel {
    TRACE = 'TRACE',
    DEBUG = 'DEBUG',
    INFO = 'INFO',
    WARN = 'WARN',
    ERROR = 'ERROR',
    FATAL = 'FATAL',
    OFF = 'OFF'
}

export class LoggerLevelsDescriptor {
    public configuredLevel?: LogLevel;
}

export class GroupLoggerLevelsDescriptor extends LoggerLevelsDescriptor {
    public members: string[] = [];
}

export class SingleLoggerLevelsDescriptor extends LoggerLevelsDescriptor {
    public effectiveLevel?: LogLevel;
}

/**
 * Description of loggers
 */
export class LoggersDescriptor {
    public levels: LogLevel[] = []
    public loggerLevels: Map<string, SingleLoggerLevelsDescriptor> = new Map()
    public groups: Map<string, GroupLoggerLevelsDescriptor> = new Map()
}

/**
 * The CRI patterns that decide what trace logging prints.
 *
 * Each pattern is matched against the fully qualified CRI with Ant wildcards, where `*` matches
 * within one segment and `**` across segments, so
 * `srv://system-api~com.acme.HeartbeatService/*` covers every method of that service and
 * `srv://system-api~com.acme.HeartbeatService/ping` covers only that one.
 * An include wins over an exclude, so `excludes: ['**']` plus the handful of includes worth
 * watching narrows trace logging to those services alone.
 */
export class TraceLogProperties {
    /** CRIs kept in trace logging whatever the excludes say. Empty leaves the excludes to decide. */
    public includes: string[] = []
    /** CRIs left out of trace logging, request and reply both, unless an include covers them. */
    public excludes: string[] = []
}

/**
 * Provides the ability to manage loggers
 */
export interface ILogManager {

    /**
     * @param nodeId the kinotic node to get the LoggersDescriptor from
     * @return a {@link LoggersDescriptor} containing all the loggers and their levels
     */
    loggers(nodeId: string): Promise<LoggersDescriptor>

    /**
     * @param nodeId the kinotic node to get the LoggerLevelsDescriptor from
     * @param name the name of the logger to get
     * @return a {@link LoggerLevelsDescriptor} containing the logger and its levels
     */
    loggerLevels(nodeId: string, name: string): Promise<LoggerLevelsDescriptor>

    /**
     * Configures the log level for the logger with the given name
     * @param nodeId the kinotic node to set the log level on
     * @param name the name of the logger to set
     * @param level the {@link LogLevel} to set for the logger with the given name
     */
    configureLogLevel(nodeId: string, name: string, level: LogLevel): Promise<void>

    /**
     * @param nodeId the kinotic node to get the trace log patterns from
     * @return the CRI patterns currently deciding what the node trace logs
     */
    traceLog(nodeId: string): Promise<TraceLogProperties>

    /**
     * Configures the CRI patterns deciding what the node trace logs, silencing a service that
     * would otherwise bury the log while trace logging is on.
     * The patterns replace whatever the node is using and last until it restarts, which returns it
     * to the `kinotic.traceLog` it was configured with.
     *
     * @param nodeId the kinotic node to set the trace log patterns on
     * @param traceLog the include and exclude patterns to apply
     */
    configureTraceLog(nodeId: string, traceLog: TraceLogProperties): Promise<void>
}
