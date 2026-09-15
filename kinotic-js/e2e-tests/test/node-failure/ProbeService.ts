import {Publish} from '@kinotic-ai/core'
import {finalize, interval, Observable} from 'rxjs'

/** What the {@link ProbeService} reports to the suite hosting it, as it happens on the serving side. */
export type ProbeEvent = 'hang-started' | 'ticks-started' | 'ticks-cancelled'

/**
 * The service the node-failure suite hosts on a client, so a test can observe the serving side of a call
 * while the platform between it and the caller fails.
 */
@Publish('e2e.nodefailure')
export class ProbeService {

    private readonly report: (event: ProbeEvent) => void

    /**
     * @param report receives every {@link ProbeEvent} as the service produces it
     */
    constructor(report: (event: ProbeEvent) => void) {
        this.report = report
    }

    echo(value: string): string {
        return value
    }

    /**
     * Never resolves, so the only reply the caller can get is the one the platform produces when the
     * call is lost.
     */
    hang(): Promise<never> {
        this.report('hang-started')
        return new Promise<never>(() => {})
    }

    /**
     * Counts up every periodMs until the platform cancels the stream; it never completes on its own.
     * @param periodMs the time between values
     */
    ticks(periodMs: number): Observable<number> {
        this.report('ticks-started')
        return interval(periodMs).pipe(finalize(() => this.report('ticks-cancelled')))
    }
}
