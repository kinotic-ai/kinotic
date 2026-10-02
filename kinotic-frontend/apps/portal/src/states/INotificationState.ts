import { reactive } from 'vue'
import { createDebug } from '@kinotic-ai/frontend-common'
import { APPLICATION_STATE } from './IApplicationState'
import { USER_STATE } from './IUserState'
import { CATEGORIES } from '@/notifications/notificationCatalog'
import { NotificationCategory } from '@/notifications/NotificationCategory'
import type { NotificationItem } from '@/notifications/NotificationItem'
import { loadNotifications } from '@/notifications/loadNotifications'

const debug = createDebug('notification-state')

/** How often the feed reads the organization's activity again while the portal is open. */
const REFRESH_INTERVAL_MS = 60_000
/** How many individually read entries are remembered; older ones fall under lastReadAt. */
const READ_KEYS_LIMIT = 500

/**
 * The bell's feed of the organization's activity, what the signed-in user has read, and which
 * categories they want to hear about. The read state and the choices are kept in this browser.
 */
export interface INotificationState {
    items: NotificationItem[]
    loading: boolean
    /** Whether the feed has loaded at least once. */
    loaded: boolean
    enabled: Record<NotificationCategory, boolean>

    /** The feed's entries in the categories switched on. */
    readonly shown: NotificationItem[]
    readonly unreadCount: number
    /** Whether an unread entry reports a failure, which the bell marks in red. */
    readonly hasUnreadFailure: boolean

    isUnread(item: NotificationItem): boolean
    /** Loads the feed now and every minute after; idempotent. */
    start(): void
    refresh(): Promise<void>
    markRead(key: string): void
    markAllRead(): void
    setEnabled(category: NotificationCategory, on: boolean): void
}

interface StoredSettings {
    lastReadAt: number
    readKeys: string[]
    enabled: Partial<Record<NotificationCategory, boolean>>
}

function defaultEnabled(): Record<NotificationCategory, boolean> {
    return Object.fromEntries(Object.values(NotificationCategory)
        .map(category => [category, CATEGORIES[category].defaultOn])) as Record<NotificationCategory, boolean>
}

class NotificationState implements INotificationState {
    public items: NotificationItem[] = []
    public loading = false
    public loaded = false
    public enabled: Record<NotificationCategory, boolean> = defaultEnabled()

    private lastReadAt = 0
    private readKeys: string[] = []
    private timer: ReturnType<typeof setInterval> | null = null
    private storageKey: string | null = null

    public get shown(): NotificationItem[] {
        return this.items.filter(item => this.enabled[item.category])
    }

    public get unreadCount(): number {
        return this.shown.filter(item => this.isUnread(item)).length
    }

    public get hasUnreadFailure(): boolean {
        return this.shown.some(item => CATEGORIES[item.category].failure && this.isUnread(item))
    }

    public isUnread(item: NotificationItem): boolean {
        return item.at > this.lastReadAt && !this.readKeys.includes(item.key)
    }

    public start(): void {
        if (this.timer === null) {
            this.restore()
            void this.refresh()
            this.timer = setInterval(() => { void this.refresh() }, REFRESH_INTERVAL_MS)
        }
    }

    public async refresh(): Promise<void> {
        if (this.loading) {
            return
        }
        this.loading = true
        try {
            if (APPLICATION_STATE.allApplications.length === 0) {
                await APPLICATION_STATE.loadAllApplications()
            }
            this.items = await loadNotifications(USER_STATE.getOrganizationId(),
                                                 APPLICATION_STATE.allApplications.map(app => app.id))
            this.loaded = true
        } catch (error) {
            debug('Failed to load notifications: %O', error)
        } finally {
            this.loading = false
        }
    }

    public markRead(key: string): void {
        if (!this.readKeys.includes(key)) {
            this.readKeys = [key, ...this.readKeys].slice(0, READ_KEYS_LIMIT)
            this.persist()
        }
    }

    public markAllRead(): void {
        this.lastReadAt = Date.now()
        this.readKeys = []
        this.persist()
    }

    public setEnabled(category: NotificationCategory, on: boolean): void {
        this.enabled = { ...this.enabled, [category]: on }
        this.persist()
    }

    // The settings are per organization, so a second organization in the same browser keeps its own
    private restore(): void {
        this.storageKey = `kinotic.notifications.${USER_STATE.getOrganizationId()}`
        try {
            const raw = window.localStorage.getItem(this.storageKey)
            const stored = raw ? JSON.parse(raw) as Partial<StoredSettings> : null
            this.lastReadAt = stored?.lastReadAt ?? 0
            this.readKeys = stored?.readKeys ?? []
            this.enabled = { ...defaultEnabled(), ...(stored?.enabled ?? {}) }
        } catch (error) {
            // storage can be blocked or hold something unreadable; the defaults stand in
            debug('Failed to read notification settings: %O', error)
        }
    }

    private persist(): void {
        if (this.storageKey === null) {
            return
        }
        const settings: StoredSettings = { lastReadAt: this.lastReadAt, readKeys: this.readKeys, enabled: this.enabled }
        try {
            window.localStorage.setItem(this.storageKey, JSON.stringify(settings))
        } catch (error) {
            debug('Failed to save notification settings: %O', error)
        }
    }
}

export const NOTIFICATION_STATE = reactive(new NotificationState()) as INotificationState
