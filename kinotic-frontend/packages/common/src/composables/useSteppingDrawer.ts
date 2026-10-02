import { computed, ref, type Ref } from 'vue'

/**
 * The state of a SteppingDrawer over a list: the row it shows, whether it is open, the row's
 * place in the list, open(row) to show a row, and step(delta) to move to the previous or next.
 * Rows are matched by the key {@code keyOf} gives, so a reloaded list keeps the shown row.
 * {@code highlighted} is the row the list should mark: the shown row, kept after the drawer
 * closes until hover(row) reports the pointer on a different row.
 */
export function useSteppingDrawer<T>(rows: Ref<T[]>, keyOf: (row: T) => string | null | undefined) {
    const selected = ref<T | null>(null) as Ref<T | null>
    const visible = ref(false)
    const released = ref(false)

    const position = computed(() => {
        const key = selected.value ? keyOf(selected.value) : null
        return rows.value.findIndex(row => keyOf(row) === key) + 1
    })

    const highlighted = computed<T | null>(() => released.value ? null : selected.value)

    function open(row: T): void {
        selected.value = row
        released.value = false
        visible.value = true
    }

    function step(delta: number): void {
        selected.value = rows.value[position.value - 1 + delta] ?? selected.value
        released.value = false
    }

    function hover(row: T): void {
        if (!visible.value && selected.value && keyOf(row) !== keyOf(selected.value)) {
            released.value = true
        }
    }

    return { selected, visible, position, highlighted, open, step, hover }
}
