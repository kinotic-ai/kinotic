import type { ILogService } from '@kinotic-ai/management-api'

/** What a log view reads: a time range of history and a live tail, both as raw Loki response bytes. */
export interface LogSource {
  /**
   * The entries from start up to end, epoch milliseconds with end exclusive, newest first up to limit,
   * as a Loki query_range response.
   */
  history(start: number, end: number, limit: number): Promise<Uint8Array>
  /**
   * A live tail from start, epoch milliseconds inclusive: the entries since start, then each new one,
   * as Loki tail frames until unsubscribed. Started at a history's end, it reads on with no entry repeated.
   */
  tail(start: number): ReturnType<ILogService['tail']>
}
