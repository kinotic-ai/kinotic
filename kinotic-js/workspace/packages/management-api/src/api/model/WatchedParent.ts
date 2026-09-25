import type { WatchedType } from './WatchedType'

/**
 * The record that made a watched record and unmakes it: told when the record changes, and taking
 * the record along when it is deleted. One string on the wire, the parent's type, the scope it is
 * stored under and its id joined by colons; neither the type nor the scope holds a colon, so the
 * first two are the separators whatever the id holds.
 */
export type WatchedParent = `${WatchedType}:${string}:${string}`
