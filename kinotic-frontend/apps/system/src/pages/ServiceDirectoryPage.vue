<template>
  <div class="flex flex-col">
    <h1 class="directory__title">Service directory</h1>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :is-show-add-new="false"
      :disable-modifications="true"
      empty-state-text="No registered services"
      @update:search="tableSearch = $event"
    >
      <template #item.service="{ item }">
        <span class="font-mono text-sm">{{ item.service }}</span>
      </template>

      <template #item.zone="{ item }">
        {{ item.zone || '—' }}
      </template>

      <template #item.online="{ item }">
        <Tag :value="item.online ? 'online' : 'offline'" :severity="item.online ? 'success' : 'danger'" />
      </template>

      <template #item.mcpExposed="{ item }">
        <Tag v-if="item.mcpExposed" value="MCP" severity="info" />
        <span v-else>—</span>
      </template>

      <template #item.lastStatusChange="{ item }">
        {{ item.lastStatusChange ? formatDate(item.lastStatusChange) : '—' }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import Tag from 'primevue/tag'

import { FunctionalIterablePage, Kinotic, type IterablePage, type Pageable } from '@kinotic-ai/core'
import type { ServiceDirectoryEntry } from '@kinotic-ai/os-api'
import {
  CrudTable,
  DatetimeUtil,
  filteredPageLoader,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

interface DirectoryRow extends DescriptiveIdentifiable {
  id: string
  service: string
  serviceAddress: string
  zone: string | null
  version: string
  online: boolean
  mcpExposed: boolean
  lastStatusChange: string | null
}

const headers: CrudHeader[] = [
  { field: 'service', header: 'Service', sortable: true },
  { field: 'zone', header: 'Zone', sortable: false },
  { field: 'version', header: 'Version', sortable: false },
  { field: 'online', header: 'Liveness', sortable: false },
  { field: 'mcpExposed', header: 'Tools', sortable: false },
  { field: 'lastStatusChange', header: 'Last change', sortable: false }
]

function fetchPage(pageable: Pageable): Promise<IterablePage<ServiceDirectoryEntry>> {
  return Kinotic.systemServiceDirectory.findEntries(pageable)
                .then(page => new FunctionalIterablePage(pageable, page, fetchPage))
}

// findEntries has no server-side search, so filtering is client-side over the page
const { crudTable, tableSearch, dataSource } = useCrudTablePage(
    filteredPageLoader(
        fetchPage,
        (entry: ServiceDirectoryEntry): DirectoryRow => ({
          id: entry.id ?? '',
          service: (entry.namespace ? entry.namespace + '.' : '') + entry.name,
          serviceAddress: entry.serviceAddress,
          zone: entry.zone,
          version: entry.version,
          online: entry.online,
          mcpExposed: entry.mcpExposed,
          lastStatusChange: entry.lastStatusChange
        }),
        row => [row.service, row.zone, row.version]
    ))

const formatDate = DatetimeUtil.formatDate
</script>

<style scoped>
.directory__title {
  font-size: 1.4rem;
  font-weight: 600;
  margin-bottom: 1.25rem;
}
</style>
