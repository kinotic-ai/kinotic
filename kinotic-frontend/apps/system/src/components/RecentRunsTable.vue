<template>
  <DashboardSection :icon="LaptopMinimalCheck" :tint="TINTS.blue" title="Recent runs" :count="runs.length"
                    description="The latest job runs; open one for its tasks." :link-to="listPath">
    <div v-if="runs.length === 0" class="py-8 text-center text-sm text-muted-color">No runs yet</div>
    <DataTable v-else :value="runs" size="small" class="text-sm" row-hover @row-click="open($event.data)">
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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Tag from 'primevue/tag'

import { StatusConditionType, findStatusCondition, type JobRun, type StatusCondition } from '@kinotic-ai/management-api'
import { LaptopMinimalCheck } from '@lucide/vue'
import { DashboardSection, DatetimeUtil, TimePill, TINTS, executionStatusSeverity } from '@kinotic-ai/frontend-common'

import { scopePath, type Scope } from '@/util/scope'

/**
 * The latest few runs of a scope as a compact card whose rows open the run under that scope.
 * The owner column names the organization on the platform, the application and project inside
 * an organization.
 */
const props = defineProps<{
  runs: JobRun[]
  scope: Scope
}>()

const router = useRouter()
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

function open(run: JobRun) {
  router.push(`${listPath.value}/${encodeURIComponent(run.id ?? '')}`)
}
</script>
