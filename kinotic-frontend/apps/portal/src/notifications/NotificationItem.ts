import type { NotificationCategory } from './NotificationCategory'

/** One entry of the notification feed: something that happened in the organization. */
export interface NotificationItem {
    /** Unique within the feed, stable across reloads, so its read state survives one. */
    key: string
    category: NotificationCategory
    title: string
    detail: string | null
    /** When it happened, epoch milliseconds. */
    at: number
    /** The page where it happened. */
    to: string
}
