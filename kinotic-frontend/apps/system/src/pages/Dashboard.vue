<template>
  <div>
    <PageHeader title="Dashboard" description="The platform at a glance, and what needs an operator.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <!-- The page reads in bands, one kind of content per row: how much, what needs me, how
         healthy, what happened -->
    <div class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
        <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                  :value="stat.tag ? undefined : stat.value" :detail="stat.detail" :to="stat.to" :loading="loading && stat.value === '—'">
          <template v-if="stat.alive !== undefined" #icon>
            <HeartbeatIcon :alive="stat.alive" :size="20" :stroke-width="1.75" />
          </template>
          <template v-if="stat.tag" #default>
            <Tag :value="stat.value" :severity="stat.tag" />
          </template>
        </StatCard>
      </div>

      <AttentionList :items="attention" />

      <div class="grid gap-4 lg:grid-cols-3">
        <DashboardSection :icon="Gauge" :tint="TINTS.orange" title="Worker capacity"
                          description="Allocated on the nodes that are online." link-to="/worker-nodes" link-label="Nodes">
          <div class="p-5">
            <div v-if="nodes.length === 0" class="py-6 text-center text-sm text-muted-color">
              No worker nodes registered
            </div>
            <div v-else-if="onlineNodes.length === 0" class="py-6 text-center text-sm text-muted-color">
              None of the {{ nodes.length }} registered worker nodes are online
            </div>
            <CapacityRows v-else :capacity="capacity" />
            <div class="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-color">
              <span v-for="state in nodeStates" :key="state.label" class="flex items-center gap-1.5">
                <span class="h-2.5 w-2.5 rounded-full" :style="{ background: state.color }" />
                {{ state.label }} <b class="font-semibold text-color">{{ state.count }}</b>
              </span>
            </div>
          </div>
        </DashboardSection>

        <WorkloadStateCard :workloads="workloads" description="Every workload on the platform." view-all-to="/workloads" />

        <JobRunsByDayChart :runs="runs" view-all-to="/jobs" />
      </div>

      <RecentRunsTable :runs="recentRuns" :scope="{}" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Boxes, Building2, Gauge, LaptopMinimalCheck, Server } from '@lucide/vue'

import { Kinotic } from '@kinotic-ai/core'
import { ExecutionStatus, WorkloadStatus, type JobRun, type Workload } from '@kinotic-ai/management-api'
import type { KinoticClusterInfo, VmNode } from '@kinotic-ai/system-api'
import { DashboardSection, HeartbeatIcon, DatetimeUtil, PageHeader, StatCard, TINTS, accentColor, errorMessage, isDark, scanJobRuns } from '@kinotic-ai/frontend-common'

import AttentionList from '@/components/AttentionList.vue'
import CapacityRows from '@/components/CapacityRows.vue'
import JobRunsByDayChart from '@/components/JobRunsByDayChart.vue'
import RecentRunsTable from '@/components/RecentRunsTable.vue'
import WorkloadStateCard from '@/components/WorkloadStateCard.vue'
import { platformAttention } from '@/util/attention'
import { NodeHealth, capacityOf, loadNodes, nodeHealth } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

const DAY_MS = 24 * 60 * 60 * 1000
/** How far back the runs chart and the recent-runs list look. */
const RUN_WINDOW_DAYS = 7
const RECENT_RUN_COUNT = 5

const cluster = ref<KinoticClusterInfo | null>(null)
const nodes = ref<VmNode[]>([])
const workloads = ref<Workload[]>([])
const runs = ref<JobRun[]>([])
const organizationCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// A workload can only be placed on a node in its desired state, so the free capacity of a node
// that is draining or unreachable is not the platform's to hand out — counting it reports headroom
// no placement can actually use.
const placeableNodes = computed(() => nodes.value.filter(node => node.state.reconciled))
const capacity = computed(() => capacityOf(placeableNodes.value))
const onlineNodes = computed(() => nodes.value.filter(node => nodeHealth(node) === NodeHealth.ONLINE))

const nodeStates = computed(() => {
  const count = (health: NodeHealth) => nodes.value.filter(node => nodeHealth(node) === health).length
  return [
    { label: 'Online', count: count(NodeHealth.ONLINE), color: accentColor('green', isDark.value) },
    { label: 'Draining', count: count(NodeHealth.DRAINING), color: accentColor('amber', isDark.value) },
    { label: 'Unreachable', count: count(NodeHealth.UNREACHABLE), color: accentColor('red', isDark.value) }
  ]
})

const attention = computed(() => platformAttention(cluster.value, nodes.value, workloads.value, runs.value))

const recentRuns = computed(() => runs.value.slice(0, RECENT_RUN_COUNT))

interface Stat {
  label: string
  value: string
  detail: string
  /** Renders the value as a Tag of this severity instead of a number. */
  tag?: string
  to: string
  icon?: Component
  /** Shows a HeartbeatIcon in place of the icon: beating while true, flat while false. */
  alive?: boolean
  /** One of TINTS. */
  tint: string
}

const stats = computed<Stat[]>(() => {
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const dayAgo = Date.now() - DAY_MS
  const today = runs.value.filter(run => (DatetimeUtil.toEpochMillis(run.started) ?? 0) >= dayAgo)
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  return [
    {
      label: 'Cluster',
      value: cluster.value?.clusterState ?? '—',
      detail: cluster.value ? `${cluster.value.serverNodeCount} server nodes` : 'Whether the cluster is serving requests',
      tag: cluster.value ? (cluster.value.active ? 'success' : 'danger') : 'secondary',
      to: '/cluster',
      alive: cluster.value?.active ?? false,
      tint: cluster.value?.active ? TINTS.purple : TINTS.surface
    },
    {
      label: 'Worker nodes',
      value: nodes.value.length === 0 && !loading.value ? '0' : `${onlineNodes.value.length} / ${nodes.value.length}`,
      detail: 'online, of those registered',
      to: '/worker-nodes',
      icon: markRaw(Server),
      tint: nodes.value.length > 0 && onlineNodes.value.length === 0 ? TINTS.red : TINTS.orange
    },
    {
      label: 'Workloads',
      value: `${running}`,
      detail: `running of ${workloads.value.length}`,
      to: '/workloads',
      icon: markRaw(Boxes),
      tint: TINTS.sky
    },
    {
      label: 'Jobs · 24 h',
      value: `${today.length}`,
      detail: `${runningRuns} running now`,
      to: '/jobs',
      icon: markRaw(LaptopMinimalCheck),
      tint: TINTS.purple
    },
    {
      label: 'Organizations',
      value: organizationCount.value?.toString() ?? '—',
      detail: 'registered on the platform',
      to: '/organizations',
      icon: markRaw(Building2),
      tint: TINTS.blue
    }
  ]
})

// Each source loads on its own so one that fails leaves the others standing
async function load() {
  loading.value = true
  error.value = null
  const failures: string[] = []
  await Promise.all([
    Kinotic.clusterInfo.getClusterInfo().then(info => { cluster.value = info })
           .catch(err => failures.push(errorMessage(err, 'Failed to load cluster info'))),
    loadNodes().then(list => { nodes.value = list })
               .catch(err => failures.push(errorMessage(err, 'Failed to load worker nodes'))),
    scanWorkloads({}).then(list => { workloads.value = list })
                     .catch(err => failures.push(errorMessage(err, 'Failed to load workloads'))),
    scanJobRuns({ since: Date.now() - RUN_WINDOW_DAYS * DAY_MS }).then(list => { runs.value = list })
                                                                  .catch(err => failures.push(errorMessage(err, 'Failed to load job runs'))),
    Kinotic.systemOrganizations.countOrganizations().then(count => { organizationCount.value = count })
           .catch(() => { /* the tile shows an em dash */ })
  ])
  error.value = failures.length > 0 ? failures.join('. ') : null
  loading.value = false
}

onMounted(load)
</script>
