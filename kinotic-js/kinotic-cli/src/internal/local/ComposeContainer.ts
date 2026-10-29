/** A container of the stack, as `docker compose ps --format json` describes it. */
export interface ComposeContainer {
    /** The compose service the container runs. */
    Service: string
    /** The container's state: `running`, `exited`, `created`, … */
    State: string
    /** The exit code of an exited container, 0 otherwise. */
    ExitCode: number
}
