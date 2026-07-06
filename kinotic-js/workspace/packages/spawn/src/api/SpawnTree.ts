/**
 * The files of a spawn or of a rendered result, keyed by POSIX-style relative
 * path (no leading slash). Text files are strings; binary files are
 * {@link Uint8Array} and are copied verbatim, never rendered.
 */
export type SpawnTree = Record<string, string | Uint8Array>
