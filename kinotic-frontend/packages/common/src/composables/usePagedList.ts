import { computed, watch, type Ref, type WatchSource } from 'vue'
import { usePaging } from './usePaging'

/**
 * Pages a list held in memory for a TablePaginator: {@code pageItems} are the items of the page
 * shown, with the {@code first}, {@code pageSize} and {@code onPage} of {@link usePaging}. The
 * list starts over at its first page when one of {@code resetOn} changes, such as a search or a
 * filter, and when it shrinks to end before the page shown.
 */
export function usePagedList<T>(items: Ref<T[]>, initialPageSize: number, resetOn: WatchSource[]) {
    const { first, pageSize, onPage } = usePaging(initialPageSize)
    const pageItems = computed(() => items.value.slice(first.value, first.value + pageSize.value))

    watch(resetOn, () => {
        first.value = 0
    })

    watch(() => items.value.length, length => {
        if (first.value >= length) {
            first.value = 0
        }
    })

    return { first, pageSize, pageItems, onPage }
}
