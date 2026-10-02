<template>
  <DashboardSection :icon="LaptopMinimalCheck" :tint="tint" title="Recent runs" :count="runs.length"
                    description="The latest job runs; open one for its tasks." :link-to="listPath">
    <EmptyChartCharacter v-if="runs.length === 0" class="py-6" title="No runs yet" />
    <DataTable v-else :value="runs" data-key="id" selection-mode="single" :selection="highlightedRun"
               size="small" class="text-sm" row-hover @row-click="openRun($event.data)"
               @mouseover="onRowsMouseover">
      <Column header="Run">
        <template #body="{ data }">
          <span class="block max-w-[24rem] cursor-pointer truncate" v-tooltip.top="data.description ?? data.name">{{ data.name }}</span>
        </template>
      </Column>
      <Column header="Status">
        <template #body="{ data }">
          <Tag :value="data.status" :severity="executionStatusSeverity(data.status)" />
          <Tag v-if="nodeLeft(data)" value="node left" severity="warn" icon="pi pi-exclamation-triangle" class="ml-1"
               v-tooltip.top="nodeLeft(data)?.message" />
        </template>
      </Column>
      <Column :header="ownerHeader" class="hidden md:table-cell">
        <template #body="{ data }">
          <span v-if="ownerOf(data)" class="font-mono">{{ ownerOf(data) }}</span>
          <span v-else class="text-muted-color">{{ ownerFallback }}</span>
        </template>
      </Column>
      <Column v-if="!scope.organizationId" header="Ran on" class="hidden md:table-cell">
        <template #body="{ data }"><span class="font-mono text-xs">{{ data.nodeId ?? '—' }}</span></template>
      </Column>
      <Column header="Started" class="hidden md:table-cell">
        <template #body="{ data }"><TimePill :date="data.started" /></template>
      </Column>
      <Column header="Duration" style="width: 8rem">
        <template #body="{ data }"><span class="font-mono text-xs tabular-nums">{{ formatDuration(data.started, data.finished) }}</span></template>
      </Column>
    </DataTable>
  </DashboardSection>

  <!-- The picked run opens beside the card; the arrows step through the runs it lists -->
  <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="runs.length"
                  :expand-to="selectedRun?.id ? `${listPath}/${encodeURIComponent(selectedRun.id)}` : undefined"
                  expand-label="Open the run's page" @step="stepRun">
    <template #title>
      <template v-if="selectedRun">
        <span class="truncate text-sm font-medium text-surface-950 dark:text-surface-0" v-tooltip.bottom="selectedRun.name">{{ selectedRun.name }}</span>
        <Tag :value="selectedRun.status" :severity="executionStatusSeverity(selectedRun.status)" class="shrink-0" />
      </template>
    </template>
    <JobRunDetail v-if="selectedRun?.id" :job-run-id="selectedRun.id" :organization-id="scope.organizationId" />
  </SteppingDrawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Tag from 'primevue/tag'

import { StatusConditionType, findStatusCondition, type JobRun, type StatusCondition } from '@kinotic-ai/management-api'
import { LaptopMinimalCheck } from '@lucide/vue'
import { DashboardSection, DatetimeUtil, SteppingDrawer, TimePill, executionStatusSeverity, EmptyChartCharacter,
         useSteppingDrawer } from '@kinotic-ai/frontend-common'

import JobRunDetail from '@/components/JobRunDetail.vue'
import { scopePath, type Scope } from '@/util/scope'

/**
 * The latest few runs of a scope as a compact card; a clicked run opens in a drawer beside it,
 * stepping through the runs the card lists, with a link to the run's page under that scope.
 * The owner column names the organization on the platform, the application and project inside
 * an organization.
 */
const props = defineProps<{
  /** The icon tint of the page it sits on, one of TINTS. */
  tint: string
  runs: JobRun[]
  scope: Scope
}>()

const formatDuration = DatetimeUtil.formatDuration

/** The mark that the node running the run left the cluster while it was live, or undefined. */
function nodeLeft(run: JobRun): StatusCondition | undefined {
  return findStatusCondition(run.state.conditions, StatusConditionType.SERVER_NODE_LEFT)
}

const listPath = computed(() => `${scopePath(props.scope)}/jobs`)

const ownerHeader = computed(() => props.scope.organizationId ? 'Application / Project' : 'Organization')
const ownerFallback = computed(() => props.scope.organizationId ? 'organization' : 'platform')

function ownerOf(run: JobRun): string | null {
  let ret: string | null
  if (props.scope.organizationId) {
    ret = run.applicationId ? [run.applicationId, run.projectId].filter(Boolean).join(' / ') : null
  } else {
    ret = run.organizationId
  }
  return ret
}

const shownRuns = computed(() => props.runs)

// The pointer moving onto a run lets a kept selection go; rows are found by their place in the body
function onRowsMouseover(event: MouseEvent) {
  const row = (event.target as Element | null)?.closest?.('tbody > tr')
  const index = row?.parentElement ? Array.from(row.parentElement.children).indexOf(row) : -1
  const run = index >= 0 ? props.runs[index] : undefined
  if (run) {
    hoverRun(run)
  }
}
const { selected: selectedRun, visible: drawerVisible, position, open: openRun, step: stepRun,
        highlighted: highlightedRun, hover: hoverRun } =
    useSteppingDrawer(shownRuns, run => run.id)
</script>
