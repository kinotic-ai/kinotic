<template>
  <div class="flex flex-col">
    <PageHeader title="Workloads" :description="description">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="mb-4 flex flex-wrap items-center gap-3">
      <StatusChips v-model="statusFilter" :chips="chips" />
      <Select
        v-if="!applicationId"
        v-model="applicationFilter"
        :options="APPLICATION_STATE.allApplications"
        option-label="id"
        option-value="id"
        placeholder="All applications"
        show-clear
        size="small"
        class="w-56 md:ml-auto"
      />
    </div>

    <WorkloadsTable :workloads="shown" :scope="scope" :workload-route="id => workloadPath(scope, id)" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { PageHeader, StatusChips, WORKLOAD_STATES, WorkloadsTable, countByStatus, errorMessage, workloadStateLabel,
         type StatusChip, type ViewScope } from '@kinotic-ai/frontend-common'

import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'
import { scopeName, workloadPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/**
 * The workloads of the organization, one of its applications, or one of its projects, with
 * state chips and, across the organization, an application filter. The filters live in the
 * URL so other pages can link to a slice of the list.
 */
const props = defineProps<{
  applicationId?: string
  projectId?: string
}>()

const route = useRoute()
const router = useRouter()

const scope = computed<ViewScope>(() => ({
  organizationId: USER_STATE.getOrganizationId(),
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const description = computed(() =>
    `The VMs the platform runs for ${scopeName(scope.value)}: each project's microservices, and the one-off runs that `
    + 'sync projects and publish or remove their UIs. A run that has ended keeps its record, its exit code and its logs.')

const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function queryParam(name: string): string | null {
  const value = route.query[name]
  return typeof value === 'string' && value !== '' ? value : null
}

function setQueryParam(name: string, value: string | null) {
  router.replace({ query: { ...route.query, [name]: value ?? undefined } })
}

const statusFilter = computed<string | null>({
  get: () => WORKLOAD_STATES.includes(queryParam('status') as WorkloadStatus) ? queryParam('status') : null,
  set: value => setQueryParam('status', value)
})

const applicationFilter = computed<string | null>({
  get: () => queryParam('app'),
  set: value => setQueryParam('app', value)
})

const chips = computed<StatusChip[]>(() => {
  const counts = countByStatus(workloads.value)
  return [
    { label: 'All', value: null, count: workloads.value.length },
    ...WORKLOAD_STATES.filter(state => counts[state] > 0)
                      .map(state => ({ label: workloadStateLabel(state), value: state, count: counts[state] }))
  ]
})

const shown = computed(() => statusFilter.value
    ? workloads.value.filter(workload => workload.status === statusFilter.value)
    : workloads.value)

async function load() {
  loading.value = true
  error.value = null
  try {
    // Across the organization the application filter narrows the read itself
    const applicationId = props.applicationId ?? applicationFilter.value ?? undefined
    workloads.value = await scanWorkloads({ ...scope.value, applicationId })
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load workloads')
  } finally {
    loading.value = false
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.applicationId, props.projectId, applicationFilter.value], load)

onMounted(() => {
  load()
  if (!props.applicationId && APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications()
  }
})
</script>
