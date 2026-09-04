<template>
  <div class="rounded-lg border border-surface">
    <div class="flex items-start justify-between gap-3 px-4 pt-4 pb-2">
      <h2 class="text-base font-semibold">Recent runs</h2>
      <RouterLink :to="listPath" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="runs.length === 0" class="px-4 pb-6 pt-2 text-center text-sm text-muted-color">No runs yet</div>
    <DataTable v-else :value="runs" size="small" class="text-sm" row-hover @row-click="open($event.data)">
      <Column header="Run">
        <template #body="{ data }">
          <span class="block max-w-[24rem] cursor-pointer truncate" :title="data.description ?? data.name">{{ data.name }}</span>
        </template>
      </Column>
      <Column header="Status">
        <template #body="{ data }"><Tag :value="data.status" :severity="executionStatusSeverity(data.status)" /></template>
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
        <template #body="{ data }">{{ formatEpochDateTime(data.started) }}</template>
      </Column>
      <Column header="Duration" style="width: 8rem">
        <template #body="{ data }">{{ formatDuration(data.started, data.finished) }}</template>
      </Column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Tag from 'primevue/tag'

import type { JobRun } from '@kinotic-ai/management-api'
import { DatetimeUtil, executionStatusSeverity } from '@kinotic-ai/frontend-common'

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
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatDuration = DatetimeUtil.formatDuration

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
