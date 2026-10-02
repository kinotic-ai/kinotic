<template>
  <div class="flex flex-col">
    <PageHeader title="Organizations" description="Every organization registered on the platform." />

    <!-- the tenants at a glance: how many there are and how much they run -->
    <section v-if="organizationCount !== null"
             class="mb-4 flex flex-wrap items-center gap-x-10 gap-y-3 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30">
      <div class="flex items-center gap-3">
        <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', TINTS.green]">
          <Building2 :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="leading-tight">
          <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Organizations</div>
          <div class="mt-0.5 text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ organizationCount }}</div>
        </div>
      </div>
      <div class="leading-tight">
        <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Running workloads</div>
        <div class="mt-0.5 flex items-center gap-2 text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">
          <span class="h-2 w-2 rounded-full bg-green-500" aria-hidden="true" />{{ runningTotal }}
        </div>
      </div>
    </section>

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
      :selected-id="highlightedOrganization?.id"
      @row-hover="row => hoverOrganization(row as OrganizationRow)"
      @on-row-click="row => openOrganization(row as OrganizationRow)"
    >
      <template #item.name="{ item, index }">
        <span class="flex min-w-0 items-center gap-2.5">
          <InitialsTile :name="item.name || item.id" :index="index" />
          <span class="min-w-0">
            <span class="block truncate font-sans text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="item.name">{{ item.name }}</span>
            <span class="block truncate text-xs text-muted-color">{{ item.id }}</span>
          </span>
        </span>
      </template>

      <template #item.applications="{ item }">
        <TableChip :icon="LayoutGrid">{{ item.applications ?? '—' }} {{ item.applications === 1 ? 'app' : 'apps' }}</TableChip>
      </template>

      <template #item.running="{ item }">
        <TableChip>
          <span :class="['h-2 w-2 rounded-full', item.running > 0 ? 'bg-green-500' : 'bg-surface-400']" aria-hidden="true" />
          {{ item.running }} running
        </TableChip>
      </template>

      <template #item.members="{ item }">
        <TableChip :icon="Users">{{ item.members ?? '—' }} {{ item.members === 1 ? 'member' : 'members' }}</TableChip>
      </template>

      <template #item.created="{ item }">
        <TimePill :date="item.created" />
      </template>
    </CrudTable>

    <!-- The picked organization opens beside the list; the arrows step through the rows the table shows -->
    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownOrganizations.length"
                    :expand-to="selectedOrganization?.id ? organizationPath(selectedOrganization.id) : undefined"
                    expand-label="Open the organization" @step="stepOrganization">
      <template #title>
        <span v-if="selectedOrganization" class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedOrganization.name }}</span>
      </template>
      <OrgOverview v-if="selectedOrganization?.id" :key="selectedOrganization.id" :organization-id="selectedOrganization.id" />
    </SteppingDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Building2, LayoutGrid, Users } from '@lucide/vue'

import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage, type Page } from '@kinotic-ai/core'
import { WorkloadStatus, type Organization } from '@kinotic-ai/management-api'
import {
  CrudTable,
  InitialsTile,
  PageHeader,
  TableChip,
  TimePill,
  TINTS,
  SteppingDrawer,
  useCrudTablePage,
  useSteppingDrawer,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import OrgOverview from '@/pages/OrgOverview.vue'
import { organizationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/** One row: the organization with the counts that say how much is going on in it. */
interface OrganizationRow extends DescriptiveIdentifiable {
  id: string
  name: string
  applications: number | null
  running: number
  members: number | null
  created: number | null
}


const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true, width: '34%' },
  { field: 'applications', header: 'Apps', sortable: false, optional: true, width: '16%' },
  { field: 'running', header: 'Running', sortable: false, optional: true, width: '16%' },
  { field: 'members', header: 'Members', sortable: false, optional: true, width: '16%' },
  { field: 'created', header: 'Created', sortable: true, optional: true, width: '18%' }
]

// Running workloads per organization, from one scan shared by every page of the table
const runningByOrganization = ref<Record<string, number>>({})
const runningTotal = computed(() => Object.values(runningByOrganization.value).reduce((sum, count) => sum + count, 0))
const organizationCount = ref<number | null>(null)

const { tableSearch, dataSource, refreshTable, shownRows } = useCrudTablePage(load)

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
    applications,
    running: runningByOrganization.value[id] ?? 0,
    members,
    created: org.created
  }
}

const shownOrganizations = computed(() => shownRows.value as OrganizationRow[])
const { selected: selectedOrganization, visible: drawerVisible, position, open: openOrganization, step: stepOrganization,
        highlighted: highlightedOrganization, hover: hoverOrganization } = useSteppingDrawer(shownOrganizations, row => row.id)

onMounted(async () => {
  Kinotic.systemOrganizations.countOrganizations().then(count => { organizationCount.value = count })
         .catch(() => { /* the summary stays hidden; the table still lists */ })
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
