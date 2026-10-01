import type { ILogService } from '@kinotic-ai/management-api'

/** What a log view reads: a time range of history and a live tail, both as raw Loki response bytes. */
export interface LogSource {
  /** The entries between start and end, epoch milliseconds, newest first up to limit, as a Loki query_range response. */
  history(start: number, end: number, limit: number): Promise<Uint8Array>
  /** A live tail emitting Loki tail frames until unsubscribed. */
  tail(): ReturnType<ILogService['tail']>
}
