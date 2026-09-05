<template>
  <div class="flex flex-col">
    <PageHeader title="Jobs" :description="description">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined @click="refresh" />
      </template>
    </PageHeader>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <JobRunsTable ref="jobRunsTable"
                  :organization-id="scope.organizationId"
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
import { JobRunsTable, PageHeader, scanJobRuns } from '@kinotic-ai/frontend-common'

import StatusChips, { type StatusChip } from '@/components/StatusChips.vue'
import { scopeName, scopePath, type Scope } from '@/util/scope'

/**
 * The job runs of the scope the route names: every run on the platform, or the deployments and
 * provisioning of an organization, an application or a project. State chips carry the counts
 * and live in the URL so a tile can link to the failed runs.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const CHIP_STATES = [ExecutionStatus.RUNNING, ExecutionStatus.COMPLETED, ExecutionStatus.FAILED]

const route = useRoute()
const router = useRouter()
const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const description = computed(() => scope.value.organizationId
    ? `Job runs executed for ${scopeName(scope.value)}: its deployments${scope.value.applicationId ? '' : ' and its provisioning'}.`
    : 'Grind job runs across the platform: deployments and organization provisioning, step by step.')

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
watch(() => [scope.value.organizationId, scope.value.applicationId, scope.value.projectId], count, { immediate: true })
</script>
