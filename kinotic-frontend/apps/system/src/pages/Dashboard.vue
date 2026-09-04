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
        <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
      </div>

      <AttentionList :items="attention" />

      <div class="grid gap-4 lg:grid-cols-3">
        <div class="rounded-lg border border-surface p-4">
          <div class="mb-3 flex items-start justify-between gap-3">
            <div>
              <h2 class="text-base font-semibold">Worker capacity</h2>
              <p class="text-xs text-muted-color">Allocated on the nodes that are online.</p>
            </div>
            <RouterLink to="/worker-nodes" class="whitespace-nowrap text-sm text-muted-color hover:text-color">Nodes</RouterLink>
          </div>
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

        <WorkloadStateCard :workloads="workloads" description="Every workload on the platform." view-all-to="/workloads" />

        <JobRunsByDayChart :runs="runs" view-all-to="/jobs" />
      </div>

      <RecentRunsTable :runs="recentRuns" :scope="{}" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { DeploymentStatusType, ExecutionStatus, WorkloadStatus,
         type JobRun, type Organization, type Workload } from '@kinotic-ai/management-api'
import { VmNodeStatusType, type KinoticClusterInfo, type VmNode } from '@kinotic-ai/system-api'
import { PageHeader, accentColor, errorMessage, isDark, scanJobRuns } from '@kinotic-ai/frontend-common'

import AttentionList from '@/components/AttentionList.vue'
import CapacityRows from '@/components/CapacityRows.vue'
import JobRunsByDayChart from '@/components/JobRunsByDayChart.vue'
import RecentRunsTable from '@/components/RecentRunsTable.vue'
import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
import WorkloadStateCard from '@/components/WorkloadStateCard.vue'
import { platformAttention } from '@/util/attention'
import { capacityOf, loadNodes } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

const DAY_MS = 24 * 60 * 60 * 1000
/** How far back the runs chart and the recent-runs list look. */
const RUN_WINDOW_DAYS = 7
/** How many organizations the ready-to-deploy count and the attention list consider. */
const ORGANIZATION_PAGE_SIZE = 100
const RECENT_RUN_COUNT = 5

const cluster = ref<KinoticClusterInfo | null>(null)
const nodes = ref<VmNode[]>([])
const workloads = ref<Workload[]>([])
const runs = ref<JobRun[]>([])
const organizations = ref<Organization[]>([])
const organizationCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// A workload can only be placed on an ONLINE node, so an offline node's free capacity is not
// the platform's to hand out — counting it reports headroom no placement can actually use.
const onlineNodes = computed(() => nodes.value.filter(node => node.status.type === VmNodeStatusType.ONLINE))
const capacity = computed(() => capacityOf(onlineNodes.value))

const nodeStates = computed(() => {
  const count = (type: VmNodeStatusType) => nodes.value.filter(node => node.status.type === type).length
  return [
    { label: 'Online', count: count(VmNodeStatusType.ONLINE), color: accentColor('green', isDark.value) },
    { label: 'Draining', count: count(VmNodeStatusType.DRAINING), color: accentColor('amber', isDark.value) },
    { label: 'Offline', count: count(VmNodeStatusType.OFFLINE), color: accentColor('red', isDark.value) }
  ]
})

const attention = computed(() => platformAttention(cluster.value, nodes.value, workloads.value, runs.value, organizations.value))

const recentRuns = computed(() => runs.value.slice(0, RECENT_RUN_COUNT))

interface Stat {
  label: string
  value: string
  description: string
  tag?: string
  to?: string
  icon?: string
  accent?: StatTileAccent
}

const stats = computed<Stat[]>(() => {
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const dayAgo = Date.now() - DAY_MS
  const today = runs.value.filter(run => (run.started ?? 0) >= dayAgo)
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  const ready = organizations.value.filter(org => org.storage?.status.type === DeploymentStatusType.READY).length
  return [
    {
      label: 'Cluster',
      value: cluster.value?.clusterState ?? '—',
      description: cluster.value ? `${cluster.value.serverNodeCount} server nodes` : 'Whether the cluster is serving requests',
      tag: cluster.value ? (cluster.value.active ? 'success' : 'danger') : 'secondary',
      to: '/cluster',
      icon: 'pi-shield',
      accent: cluster.value && !cluster.value.active ? 'red' : 'green'
    },
    {
      label: 'Worker nodes',
      value: nodes.value.length === 0 && !loading.value ? '0' : `${onlineNodes.value.length} / ${nodes.value.length}`,
      description: 'online, of those registered',
      to: '/worker-nodes',
      icon: 'pi-server',
      accent: nodes.value.length > 0 && onlineNodes.value.length === 0 ? 'red' : 'amber'
    },
    {
      label: 'Workloads',
      value: `${running}`,
      description: `running of ${workloads.value.length}`,
      to: '/workloads',
      icon: 'pi-box',
      accent: 'sky'
    },
    {
      label: 'Jobs · 24 h',
      value: `${today.length}`,
      description: `${runningRuns} running now`,
      to: '/jobs',
      icon: 'pi-list-check',
      accent: 'violet'
    },
    {
      label: 'Organizations',
      value: organizationCount.value?.toString() ?? '—',
      description: `${ready} ready to deploy`,
      to: '/organizations',
      icon: 'pi-building',
      accent: 'teal'
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
    Kinotic.systemOrganizations.findOrganizations(Pageable.create(0, ORGANIZATION_PAGE_SIZE))
           .then(page => { organizations.value = page.content ?? [] })
           .catch(err => failures.push(errorMessage(err, 'Failed to load organizations'))),
    Kinotic.systemOrganizations.countOrganizations().then(count => { organizationCount.value = count })
           .catch(() => { /* the tile shows an em dash */ })
  ])
  error.value = failures.length > 0 ? failures.join('. ') : null
  loading.value = false
}

onMounted(load)
</script>
