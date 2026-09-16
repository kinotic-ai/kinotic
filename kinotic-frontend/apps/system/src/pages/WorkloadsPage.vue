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
      <div class="flex flex-wrap items-center gap-2 md:ml-auto">
        <Select
          v-if="!scope.organizationId"
          v-model="organizationFilter"
          :options="organizationOptions"
          option-label="label"
          option-value="value"
          placeholder="Any organization"
          show-clear
          size="small"
          class="w-56"
        />
        <Select
          v-model="nodeFilter"
          :options="nodeOptions"
          option-label="label"
          option-value="value"
          placeholder="Any node"
          show-clear
          size="small"
          class="w-48"
        />
      </div>
    </div>

    <WorkloadsTable :workloads="shown" :scope="scope" :node-names="nodeNames" @changed="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus, type Organization, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { PageHeader, errorMessage } from '@kinotic-ai/frontend-common'

import StatusChips, { type StatusChip } from '@/components/StatusChips.vue'
import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { loadNodes } from '@/util/nodes'
import { scopeName, type Scope } from '@/util/scope'
import { PLATFORM_ONLY, WORKLOAD_STATES, countByStatus, scanWorkloads, workloadStateLabel } from '@/util/workloads'

/**
 * The workloads of the scope the route names — the whole platform, an organization, an
 * application or a project — with state chips and, on the platform, organization and node
 * filters. The filters live in the URL so other pages can link to a slice of the list.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

/** How many organizations the organization filter lists. */
const ORGANIZATION_PAGE_SIZE = 100

const route = useRoute()
const router = useRouter()

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const description = computed(() => scope.value.organizationId
    ? `The VMs running for ${scopeName(scope.value)}: its microservices, and the one-off sync and publish workloads its deployments start.`
    : 'Every VM the platform has placed: microservices, one-off sync and publish workloads, and the platform\'s own.')

const workloads = ref<Workload[]>([])
const nodes = ref<VmNode[]>([])
const organizations = ref<Organization[]>([])
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

const organizationFilter = computed<string | null>({
  get: () => queryParam('org'),
  set: value => setQueryParam('org', value)
})

const nodeFilter = computed<string | null>({
  get: () => queryParam('node'),
  set: value => setQueryParam('node', value)
})

const organizationOptions = computed(() => [
  { label: 'Platform only', value: PLATFORM_ONLY },
  ...organizations.value.map(org => ({ label: org.name, value: org.id ?? '' }))
])

const nodeOptions = computed(() => nodes.value.map(node => ({ label: node.name, value: node.id })))

const nodeNames = computed(() => Object.fromEntries(nodes.value.map(node => [node.id, node.name])))

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
    // On the platform the organization filter narrows the scan itself
    const org = scope.value.organizationId ? null : organizationFilter.value
    const scanScope: Scope = org && org !== PLATFORM_ONLY ? { organizationId: org } : scope.value
    const [list, nodeList] = await Promise.all([
      scanWorkloads(scanScope, { platformOnly: org === PLATFORM_ONLY, nodeId: nodeFilter.value ?? undefined }),
      loadNodes()
    ])
    workloads.value = list
    nodes.value = nodeList
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load workloads')
  } finally {
    loading.value = false
  }
}

async function loadOrganizations() {
  if (scope.value.organizationId) return
  try {
    const page = await Kinotic.systemOrganizations.findOrganizations(Pageable.create(0, ORGANIZATION_PAGE_SIZE))
    organizations.value = page.content ?? []
  } catch {
    // The filter lists the platform alone; the page still works without the organizations
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [scope.value.organizationId, scope.value.applicationId, scope.value.projectId, organizationFilter.value, nodeFilter.value], load)

onMounted(() => {
  load()
  loadOrganizations()
})
</script>
