import type { WatchedType } from './WatchedType'

/**
 * The record that made a watched record and unmakes it: told when the record changes, and taking
 * the record along when it is deleted. One string on the wire, the parent's type and id joined by a
 * colon; the type never holds a colon, so the first one is the separator whatever the id holds.
 */
export type WatchedParent = `${WatchedType}:${string}`
