import { ref } from 'vue'

/** form: filling in the fields; creating: the request is in flight; ready: the server created it. */
export type CreationPhase = 'form' | 'creating' | 'ready'

// Long enough to read the status view even when the server answers instantly
const MIN_CREATING_MS = 700
// How long the finished status stays up before the drawer closes
const READY_HOLD_MS = 1200

/**
 * Tracks a create drawer through form → creating → ready. `create` runs the request, holding the
 * creating phase long enough to read, and returns to the form when the request fails;
 * `holdReady` resolves once the ready status has been shown long enough to read.
 */
export function useCreationPhase() {
    const phase = ref<CreationPhase>('form')

    async function create<T>(request: () => Promise<T>): Promise<T> {
        phase.value = 'creating'
        try {
            const [result] = await Promise.all([request(), delay(MIN_CREATING_MS)])
            phase.value = 'ready'
            return result
        } catch (error) {
            phase.value = 'form'
            throw error
        }
    }

    function holdReady(): Promise<void> {
        return delay(READY_HOLD_MS)
    }

    return { phase, create, holdReady }
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
}
