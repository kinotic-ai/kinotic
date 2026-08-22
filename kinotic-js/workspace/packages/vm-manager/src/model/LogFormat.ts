
/**
 * How the lines in a {@link LogTarget}'s files are encoded, which decides what the log
 * shipper must do to recover the message the workload actually wrote.
 */
export enum LogFormat {

    /** Each line is the log message, as the workload wrote it. */
    PLAIN = 'PLAIN',

    /**
     * Docker's json-file driver: each line is a JSON object carrying the message, the
     * stream it came from, and the time the daemon received it.
     */
    DOCKER_JSON = 'DOCKER_JSON'
}
