<template>
  <CrudTable
    ref="crudTable"
    :headers="headers"
    :data-source="dataSource"
    :search="tableSearch"
    :is-show-add-new="false"
    :disable-modifications="true"
    empty-state-text="No applications"
    @update:search="tableSearch = $event"
  >
    <template #item.id="{ item }">
      <span class="font-mono text-sm">{{ item.id }}</span>
    </template>

    <template #item.description="{ item }">
      {{ item.description || '—' }}
    </template>

    <template #item.updated="{ item }">
      {{ item.updated ? formatDate(item.updated) : '—' }}
    </template>
  </CrudTable>
</template>

<script setup lang="ts">
import { FunctionalIterablePage, Kinotic, type IterablePage, type Pageable } from '@kinotic-ai/core'
import type { Application } from '@kinotic-ai/os-api'
import {
  CrudTable,
  DatetimeUtil,
  filteredPageLoader,
  useCrudTablePage,
  type CrudHeader
} from '@kinotic-ai/frontend-common'

const props = defineProps<{
  organizationId: string
}>()

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'id', header: 'Id', sortable: false },
  { field: 'description', header: 'Description', sortable: false },
  { field: 'updated', header: 'Updated', sortable: false }
]

function fetchPage(pageable: Pageable): Promise<IterablePage<Application>> {
  return Kinotic.systemOrganizations.findApplications(props.organizationId, pageable)
                .then(page => new FunctionalIterablePage(pageable, page, fetchPage))
}

// findApplications has no server-side search, so filtering is client-side over the page
const { crudTable, tableSearch, dataSource } = useCrudTablePage(
    filteredPageLoader(
        fetchPage,
        (app: Application) => ({ id: app.id ?? '', name: app.name, description: app.description, updated: app.updated }),
        row => [row.name ?? null, row.id, row.description ?? null]
    ))

const formatDate = DatetimeUtil.formatEpochDate
</script>
