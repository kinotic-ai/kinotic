import { computed, ref } from 'vue'
import type { PageState } from 'primevue/paginator'

/**
 * The paging state of a table with a TablePaginator: {@code first} is the index of the first row
 * of the page shown, {@code pageSize} how many rows a page holds and {@code page} the page shown,
 * counted from 0. {@code onPage} takes the paginator's page event, then calls {@code changed},
 * where a table that reads its pages from a service reads the page now shown.
 */
export function usePaging(initialPageSize: number, changed?: () => void) {
    const first = ref(0)
    const pageSize = ref(initialPageSize)
    const page = computed(() => Math.floor(first.value / pageSize.value))

    function onPage(state: PageState): void {
        first.value = state.first
        pageSize.value = state.rows
        changed?.()
    }

    return { first, pageSize, page, onPage }
}
