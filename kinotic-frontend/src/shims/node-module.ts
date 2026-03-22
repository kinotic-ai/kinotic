// Stub for node:module — used by @kinotic-ai/core's CJS interop shim.
// In the browser Vite resolves CJS deps itself, so createRequire is never actually called.
export function createRequire() {
    return () => {
        throw new Error('createRequire is not available in the browser')
    }
}
