<template>
  <div class="flex flex-col">
    <PageHeader title="Organizations" description="Every organization registered on the platform, and whether it is ready to deploy." />

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

      <template #item.storage="{ item }">
        <Tag v-if="item.storage" :value="item.storage" :severity="deploymentStatusSeverity(item.storage)" :title="item.storageMessage ?? undefined" />
        <span v-else class="text-muted-color">Not provisioned</span>
      </template>

      <template #item.applications="{ item }">
        {{ item.applications ?? '—' }}
      </template>

      <template #item.members="{ item }">
        {{ item.members ?? '—' }}
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Tag from 'primevue/tag'

import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage, type Page } from '@kinotic-ai/core'
import { DeploymentStatusType, WorkloadStatus, type Organization } from '@kinotic-ai/management-api'
import {
  CrudTable,
  PageHeader,
  DatetimeUtil,
  deploymentStatusSeverity,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import { organizationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/** One row: the organization with the counts that say how much is going on in it. */
interface OrganizationRow extends DescriptiveIdentifiable {
  id: string
  name: string
  storage: DeploymentStatusType | null
  storageMessage: string | null
  applications: number | null
  running: number
  members: number | null
  created: number | null
}

const router = useRouter()
const formatDate = DatetimeUtil.formatEpochDate

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'id', header: 'Id', sortable: false, optional: true },
  { field: 'storage', header: 'Storage', sortable: false },
  { field: 'applications', header: 'Apps', sortable: false, optional: true },
  { field: 'running', header: 'Running', sortable: false, optional: true },
  { field: 'members', header: 'Members', sortable: false, optional: true },
  { field: 'created', header: 'Created', sortable: true, optional: true }
]

// Running workloads per organization, from one scan shared by every page of the table
const runningByOrganization = ref<Record<string, number>>({})

const { tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const orgs = searchText
      ? await Kinotic.systemOrganizations.searchOrganizations(searchText, pageable)
      : await Kinotic.systemOrganizations.findOrganizations(pageable)
  const page: Page<DescriptiveIdentifiable> = {
    content: await Promise.all((orgs.content ?? []).map(toRow)),
    totalElements: orgs.totalElements,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

// The counts come from one-row pages, whose totalElements carries the whole count; a count that
// fails leaves an em dash rather than taking the row with it
async function toRow(org: Organization): Promise<OrganizationRow> {
  const id = org.id ?? ''
  const firstPage = Pageable.create(0, 1)
  const [applications, members] = await Promise.all([
    Kinotic.systemOrganizations.findApplications(id, firstPage).then(page => page.totalElements ?? 0).catch(() => null),
    Kinotic.systemOrganizations.findMembers(id, null, firstPage).then(page => page.totalElements ?? 0).catch(() => null)
  ])
  return {
    id,
    name: org.name,
    storage: org.storage?.status.type ?? null,
    storageMessage: org.storage?.status.message ?? null,
    applications,
    running: runningByOrganization.value[id] ?? 0,
    members,
    created: org.created
  }
}

function openOrganization(row: DescriptiveIdentifiable) {
  router.push(organizationPath(row.id ?? ''))
}

onMounted(async () => {
  try {
    const counts: Record<string, number> = {}
    for (const workload of await scanWorkloads({})) {
      if (workload.organizationId && workload.status === WorkloadStatus.RUNNING) {
        counts[workload.organizationId] = (counts[workload.organizationId] ?? 0) + 1
      }
    }
    runningByOrganization.value = counts
    refreshTable()
  } catch {
    // The column shows zero; the organizations themselves still list
  }
})
</script>
