<template>
  <div class="flex flex-col">
    <h1 class="text-2xl font-semibold mb-5">Organizations</h1>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :is-show-add-new="false"
      :disable-modifications="true"
      :enable-row-hover="true"
      empty-state-text="No organizations"
      @update:search="tableSearch = $event"
      @on-row-click="openOrganization"
    >
      <template #item.id="{ item }">
        <span class="font-mono text-sm">{{ item.id }}</span>
      </template>

      <template #item.description="{ item }">
        {{ item.description || '—' }}
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { FunctionalIterablePage, Kinotic, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import {
  CrudTable,
  DatetimeUtil,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

const router = useRouter()

function openOrganization(row: DescriptiveIdentifiable) {
  router.push({ name: 'organization-detail', params: { organizationId: row.id } })
}

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'id', header: 'Id', sortable: false },
  { field: 'description', header: 'Description', sortable: false },
  { field: 'created', header: 'Created', sortable: true }
]

const { crudTable, tableSearch, dataSource } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const orgs = searchText
      ? await Kinotic.systemOrganizations.searchOrganizations(searchText, pageable)
      : await Kinotic.systemOrganizations.findOrganizations(pageable)
  const page: Page<DescriptiveIdentifiable> = {
    content: (orgs.content ?? []).map(org => ({
      id: org.id ?? '',
      name: org.name,
      description: org.description ?? undefined,
      created: org.created
    })),
    totalElements: orgs.totalElements,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

const formatDate = DatetimeUtil.formatEpochDate
</script>
