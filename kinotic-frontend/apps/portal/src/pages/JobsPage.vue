<template>
  <div class="flex flex-col">
    <PageHeader title="Jobs" :description="description">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined @click="refresh" />
      </template>
    </PageHeader>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <!-- Every run the facade returns is the organization's, so across the organization the table
         pages them from the server rather than narrowing a scan of them -->
    <JobRunsTable ref="jobRunsTable"
                  :application-id="scope.applicationId"
                  :project-id="scope.projectId"
                  :status="statusFilter"
                  @open="openRun" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'

import { ExecutionStatus, type JobRun } from '@kinotic-ai/management-api'
import { JobRunsTable, PageHeader, StatusChips, scanJobRuns, type StatusChip, type ViewScope } from '@kinotic-ai/frontend-common'

import { USER_STATE } from '@/states/IUserState'
import { scopeName, scopePath } from '@/util/scope'

/**
 * The job runs of the organization, one of its applications, or one of its projects, such as
 * deployments, step by step. State chips carry the counts and live in the URL so a tile can
 * link to the failed runs.
 */
const props = defineProps<{
  applicationId?: string
  projectId?: string
}>()

const CHIP_STATES = [ExecutionStatus.RUNNING, ExecutionStatus.COMPLETED, ExecutionStatus.FAILED]

const route = useRoute()
const router = useRouter()
const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()

const scope = computed<ViewScope>(() => ({
  organizationId: USER_STATE.getOrganizationId(),
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const description = computed(() => `Job runs executed for ${scopeName(scope.value)}, such as workload deployments, step by step.`)

const counted = ref<JobRun[]>([])

const statusFilter = computed<ExecutionStatus | null>({
  get: () => CHIP_STATES.includes(route.query.status as ExecutionStatus) ? route.query.status as ExecutionStatus : null,
  set: value => { router.replace({ query: { ...route.query, status: value ?? undefined } }) }
})

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null, count: counted.value.length },
  ...CHIP_STATES.map(state => ({
    label: state.charAt(0) + state.slice(1).toLowerCase(),
    value: state,
    count: counted.value.filter(run => run.status === state).length
  }))
])

// The chips count from a scan of the scope's runs; the table pages the runs itself
async function count() {
  try {
    counted.value = await scanJobRuns({
      organizationId: scope.value.organizationId,
      applicationId: scope.value.applicationId,
      projectId: scope.value.projectId
    })
  } catch {
    // The chips keep their last counts; the table reports the failure itself
  }
}

function refresh() {
  jobRunsTable.value?.refresh()
  count()
}

function openRun(jobRunId: string) {
  router.push(`${scopePath(scope.value)}/jobs/${encodeURIComponent(jobRunId)}`)
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [scope.value.applicationId, scope.value.projectId], count, { immediate: true })
</script>
