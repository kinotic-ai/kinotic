import type { WatchEventType } from './WatchEventType'
import type { WatchedParent } from './WatchedParent'
import type { WatchedType } from './WatchedType'

/**
 * One entry of the ledger: what happened to a watched record, from where, and why. The record itself
 * says what it is now; its entries say how it got there.
 */
export interface WatchEvent {
    /** When the write landed, as an ISO-8601 timestamp. */
    '@timestamp': string
    /** The kind of record that changed. */
    type: WatchedType
    /** The record's id. */
    id: string
    /** The record's name when written, null for a record whose id names it. */
    name: string | null
    /**
     * The scope the record is addressed under, as its repository gives it: its organization for a
     * record an organization owns, null for a node.
     */
    scope: string | null
    /** What the record belongs to, null for a record the platform made on its own. */
    parent: WatchedParent | null
    /** What happened. */
    eventType: WatchEventType
    /** What caused it: the node that reported, a system user, the orchestrator's inference. */
    source: string
    /** The server node that wrote it. */
    serverNodeId: string
    /** The record's generation when written, null for a record that carries none. */
    generation: number | null
    /** Why, for a system user. */
    message: string
    /** What was written, as JSON. */
    value: unknown
}
