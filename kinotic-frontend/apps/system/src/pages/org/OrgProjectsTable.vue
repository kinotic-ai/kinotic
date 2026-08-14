<template>
  <CrudTable
    ref="crudTable"
    :headers="headers"
    :data-source="dataSource"
    :search="tableSearch"
    :is-show-add-new="false"
    :disable-modifications="true"
    empty-state-text="No projects"
    @update:search="tableSearch = $event"
  >
    <template #item.applicationId="{ item }">
      <span class="font-mono text-sm">{{ item.applicationId }}</span>
    </template>

    <template #item.repoFullName="{ item }">
      {{ item.repoFullName || '—' }}
    </template>

    <template #item.description="{ item }">
      {{ item.description || '—' }}
    </template>
  </CrudTable>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { FunctionalIterablePage, Kinotic, type IterablePage, type Pageable } from '@kinotic-ai/core'
import type { Project } from '@kinotic-ai/os-api'
import {
  CrudTable,
  filteredPageLoader,
  useCrudTablePage,
  type CrudHeader
} from '@kinotic-ai/frontend-common'

const props = defineProps<{
  organizationId: string
}>()

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'applicationId', header: 'Application', sortable: false },
  { field: 'repoFullName', header: 'Repository', sortable: false },
  { field: 'description', header: 'Description', sortable: false }
]

function fetchPage(pageable: Pageable): Promise<IterablePage<Project>> {
  return Kinotic.systemOrganizations.findProjects(props.organizationId, pageable)
                .then(page => new FunctionalIterablePage(pageable, page, fetchPage))
}

// findProjects has no server-side search, so filtering is client-side over the page
const { crudTable, tableSearch, dataSource, refreshTable } = useCrudTablePage(
    filteredPageLoader(
        fetchPage,
        (project: Project) => ({
          id: project.id ?? '',
          name: project.name,
          applicationId: project.applicationId,
          repoFullName: project.repoFullName,
          description: project.description
        }),
        row => [row.name ?? null, row.applicationId, row.repoFullName, row.description ?? null]
    ))

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, () => refreshTable())
</script>
