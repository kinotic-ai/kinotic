import { ref } from 'vue'

const STORAGE_KEY = 'kinotic-dark-mode'

function applyMode(dark: boolean): void {
    document.documentElement.classList.toggle('dark', dark)
    try { localStorage.setItem(STORAGE_KEY, String(dark)) } catch { /* ignore */ }
}

const initial = (() => {
    try {
        const stored = localStorage.getItem(STORAGE_KEY)
        if (stored !== null) return stored === 'true'
        return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
    } catch { return false }
})()

// Apply immediately — no Vue watcher needed
applyMode(initial)

export const isDark = ref(initial)

export function toggleDark(): void {
    isDark.value = !isDark.value
    applyMode(isDark.value)
}
