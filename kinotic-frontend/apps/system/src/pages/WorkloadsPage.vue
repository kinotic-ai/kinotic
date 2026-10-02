<template>
  <div class="flex flex-col">
    <PageHeader title="Workloads" :description="description">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <!-- the workloads at a glance: how many run, the split by state in the chips' colours, and
         what the running ones hold -->
    <section v-if="workloads.length > 0"
             class="mb-4 flex flex-wrap items-center gap-x-8 gap-y-3 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30">
      <div class="flex items-center gap-3">
        <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', scopeTint(scope)]">
          <Boxes :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="leading-tight">
          <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Running</div>
          <div class="mt-0.5 text-sm"><b class="text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ running.length }}</b>
            <span class="text-muted-color"> of {{ workloads.length }} workloads</span></div>
        </div>
      </div>
      <div class="min-w-[14rem] flex-1">
        <div class="flex h-2 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800"
             role="img" :aria-label="stateSplit.map(part => `${part.count} ${part.label}`).join(', ')">
          <span v-for="part in stateSplit" :key="part.label" :class="['h-full', part.fill]"
                :style="{ width: `${part.share}%` }" v-tooltip.top="`${part.label}: ${part.count}`" />
        </div>
      </div>
      <div class="leading-tight">
        <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Held by running</div>
        <div class="mt-0.5 text-sm tabular-nums text-surface-800 dark:text-surface-100">{{ runningCpus }} CPU · {{ runningMemory }}</div>
      </div>
    </section>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <WorkloadsTable :workloads="shown" :scope="scope" :node-names="nodeNames" @changed="load">
      <!-- the filters sit on the table's search row, each marked with what it narrows by -->
      <template #toolbar>
        <Select checkmark
          v-if="!scope.organizationId"
          v-model="organizationFilter"
          :options="organizationOptions"
          option-label="label"
          option-value="value"
          placeholder="Any organization"
          show-clear
          size="small"
          class="w-56"
        >
          <template #value="{ value, placeholder }">
            <span class="flex items-center gap-2">
              <Building2 :size="15" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
              <span :class="value ? '' : 'text-muted-color'">{{ value ? labelOf(organizationOptions, value) : placeholder }}</span>
            </span>
          </template>
        </Select>
        <Select checkmark
          v-model="nodeFilter"
          :options="nodeOptions"
          option-label="label"
          option-value="value"
          placeholder="Any node"
          show-clear
          size="small"
          class="w-48"
        >
          <template #value="{ value, placeholder }">
            <span class="flex items-center gap-2">
              <Server :size="15" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
              <span :class="value ? '' : 'text-muted-color'">{{ value ? labelOf(nodeOptions, value) : placeholder }}</span>
            </span>
          </template>
        </Select>
      </template>
    </WorkloadsTable>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { Boxes, Building2, Server } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus, type Organization, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { PageHeader, errorMessage, formatMb, StatusChips, type StatusChip, SEVERITY_FILL } from '@kinotic-ai/frontend-common'

import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { formatCpus, loadNodes } from '@/util/nodes'
import { scopeName, scopeTint, type Scope } from '@/util/scope'
import { PLATFORM_ONLY, WORKLOAD_STATES, countByStatus, scanWorkloads, workloadSeverity, workloadStateLabel } from '@/util/workloads'

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

/** The label of the option holding the value, for a select's closed state. */
function labelOf(options: { label: string, value: string }[], value: string): string {
  return options.find(option => option.value === value)?.label ?? value
}

const nodeNames = computed(() => Object.fromEntries(nodes.value.map(node => [node.id, node.name])))

const chips = computed<StatusChip[]>(() => {
  const counts = countByStatus(workloads.value)
  return [
    { label: 'All', value: null, count: workloads.value.length },
    ...WORKLOAD_STATES.filter(state => counts[state] > 0)
                      .map(state => ({ label: workloadStateLabel(state), value: state, count: counts[state], severity: workloadSeverity(state) }))
  ]
})

const running = computed(() => workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING))
const runningCpus = computed(() => formatCpus(running.value.reduce((sum, workload) => sum + workload.cpus, 0)))
const runningMemory = computed(() => formatMb(running.value.reduce((sum, workload) => sum + workload.memoryMb, 0)))

/** Each state's share of the workloads, in the order and colours of the chips; empty states are left out. */
const stateSplit = computed(() => {
  const counts = countByStatus(workloads.value)
  const total = workloads.value.length || 1
  return WORKLOAD_STATES.filter(state => counts[state] > 0).map(state => ({
    label: workloadStateLabel(state),
    count: counts[state],
    share: (counts[state] / total) * 100,
    fill: SEVERITY_FILL[workloadSeverity(state)] ?? SEVERITY_FILL.secondary
  }))
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
