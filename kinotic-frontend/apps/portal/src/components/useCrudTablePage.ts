import { computed, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import {
  FunctionalIterablePage,
  type IDataSource,
  type IterablePage,
  type Page,
  type Pageable
} from '@kinotic-ai/core'
import CrudTable from '@/components/CrudTable.vue'
import type { DescriptiveIdentifiable } from '@/types/DescriptiveIdentifiable'
import { showErrorToast } from '@kinotic-ai/frontend-common'

/** The load signature a CrudTable page supplies: one page of rows, optionally filtered. */
export type PageLoader = (pageable: Pageable, searchText: string | null) => Promise<IterablePage<DescriptiveIdentifiable>>

/**
 * The state and behavior every CrudTable management page shares: the table ref and its
 * refresh, the search model, a dataSource delegating to the page's load function, and
 * {@link run} — a mutation wrapped in success/failure toasts, refreshing on success.
 */
export function useCrudTablePage(load: PageLoader) {
  const toast = useToast()
  const crudTable = ref<InstanceType<typeof CrudTable>>()
  const tableSearch = ref('')

  const dataSource = computed<IDataSource<DescriptiveIdentifiable>>(() => ({
    findAll: (pageable: Pageable) => load(pageable, null),
    search: (searchText: string, pageable: Pageable) => load(pageable, searchText)
  }))

  function refreshTable() {
    crudTable.value?.find()
  }

  async function run(action: () => Promise<void>, successMessage: string, failureMessage: string) {
    try {
      await action()
      toast.add({ severity: 'success', summary: successMessage, life: 4000 })
      refreshTable()
    } catch (err) {
      showErrorToast(toast, failureMessage, err, { life: 8000 })
    }
  }

  return { crudTable, tableSearch, dataSource, refreshTable, run }
}

/**
 * Builds a {@link PageLoader} for services without server-side search: fetches a page of
 * DTOs, maps them to rows, filters client-side over the given row fields, and re-wraps the
 * page so paging keeps the active search. Suits small collections, where filtering the
 * fetched page suffices.
 */
export function filteredPageLoader<D, Row extends DescriptiveIdentifiable>(
  fetchPage: (pageable: Pageable) => Promise<IterablePage<D>>,
  toRow: (dto: D) => Row,
  searchFields: (row: Row) => (string | null)[]
): PageLoader {
  async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
    const dtoPage = await fetchPage(pageable)
    let rows = (dtoPage.content ?? []).map(toRow)
    if (searchText) {
      const needle = searchText.trim().toLowerCase()
      rows = rows.filter(row => searchFields(row).some(field => (field ?? '').toLowerCase().includes(needle)))
    }
    const page: Page<DescriptiveIdentifiable> = {
      content: rows,
      totalElements: searchText ? rows.length : dtoPage.totalElements ?? rows.length,
      cursor: undefined
    }
    return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
  }
  return load
}

/** Severity of an identity status badge: Active is in good standing, Invited pending, the rest cut off. */
export function statusSeverity(status: string): string {
  let ret: string
  if (status === 'Active') {
    ret = 'success'
  } else if (status === 'Invited') {
    ret = 'info'
  } else {
    ret = 'danger'
  }
  return ret
}
