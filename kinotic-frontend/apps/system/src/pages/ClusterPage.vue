<template>
  <div>
    <PageHeader title="Cluster" description="The org, system and app server nodes and how they are doing as a group.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <NoAccessState v-if="refused" description="No grant you hold reaches the cluster. An administrator of the platform can grant you access from the Access page." />
    <div v-else class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                  :value="stat.tag ? undefined : stat.value" :detail="stat.detail"
                  :loading="loading && !cluster">
          <template v-if="stat.heartbeat !== undefined" #icon>
            <HeartbeatIcon :state="stat.heartbeat" :size="20" :stroke-width="1.75" />
          </template>
          <template v-if="stat.tag" #default>
            <Tag :value="stat.value" :severity="stat.tag" />
          </template>
        </StatCard>
      </div>

      <DashboardSection :icon="Server" :tint="TINTS.ink" title="Server nodes" :count="cluster?.nodes.length"
                        description="Logs follows that node's logs. Logging opens its logger levels and trace-log filters.">
        <!-- the cluster's shape at a glance: its nodes in the order they joined, linked, with a pulse
             running between them while the cluster serves -->
        <div v-if="orderedNodes.length > 0"
             class="topology flex items-center gap-0 overflow-x-auto border-b border-surface-200 px-5 py-5 dark:border-surface-700">
          <template v-for="(node, position) in orderedNodes" :key="node.nodeId">
            <div v-if="position > 0" class="relative h-px min-w-8 flex-1 overflow-visible bg-surface-300 dark:bg-surface-600" aria-hidden="true">
              <!-- the pulse: a streak of light fading out at both ends, its bright head leading -->
              <span v-if="cluster?.active" class="topology__pulse absolute -top-[2px] flex h-[5px] w-16 items-center">
                <span class="h-px flex-1 bg-gradient-to-r from-transparent via-purple-400 to-purple-500" />
                <span class="h-[5px] w-[5px] shrink-0 rounded-full bg-purple-500 shadow-[0_0_6px_rgb(168_85_247/0.8)]" />
                <span class="h-px w-3 bg-gradient-to-r from-purple-500 to-transparent" />
              </span>
            </div>
            <div class="flex shrink-0 items-center gap-2.5 rounded-xl border border-surface-200 bg-surface-0 px-3 py-2 shadow-sm dark:border-surface-700 dark:bg-surface-900">
              <span :class="['flex h-7 w-7 items-center justify-center rounded-md', TINTS.ink]">
                <Server :size="14" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <div class="leading-tight">
                <div class="text-[0.8125rem] font-semibold text-surface-950 dark:text-surface-0">{{ nodeTitle(node) }}</div>
                <div class="font-mono text-[0.6875rem] text-muted-color">#{{ node.order }} · {{ primaryAddress(node.addresses) }}</div>
              </div>
            </div>
          </template>
        </div>
        <DataTable :value="cluster?.nodes ?? []" size="small" class="text-sm" data-key="nodeId">
          <template #empty>
            <div v-if="loading" class="py-6 text-center text-sm text-muted-color">Loading cluster topology…</div>
            <EmptyChartCharacter v-else class="py-6" title="No server nodes reported" />
          </template>
          <Column header="Node" style="width: 38%">
            <template #body="{ data }">
              <span class="flex items-center gap-3">
                <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.ink]">
                  <Server :size="16" :stroke-width="1.75" aria-hidden="true" />
                </span>
                <span class="min-w-0">
                  <span class="flex items-center gap-2">
                    <span class="truncate font-semibold text-surface-950 dark:text-surface-0">{{ nodeTitle(data) }}</span>
                    <span class="shrink-0 rounded-md bg-surface-100 px-1.5 text-[0.6875rem] font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300"
                          v-tooltip.top="'Order it joined the cluster'">#{{ data.order }}</span>
                  </span>
                  <span class="mt-0.5 block truncate font-mono text-xs text-muted-color" v-tooltip.top="data.nodeId">{{ shortId(data.nodeId) }}</span>
                </span>
              </span>
            </template>
          </Column>
          <Column header="Version">
            <template #body="{ data }">
              <span class="inline-flex items-center gap-1.5">
                <span class="whitespace-nowrap rounded-md bg-surface-100 px-1.5 py-0.5 font-mono text-xs text-surface-700 dark:bg-surface-800 dark:text-surface-200"
                      v-tooltip.top="data.version ?? undefined">{{ releaseOf(data.version) }}</span>
                <Tag v-if="commonVersion && data.version !== commonVersion" value="behind" severity="warn" />
              </span>
            </template>
          </Column>
          <Column header="Address" class="hidden md:table-cell">
            <template #body="{ data }">
              <span class="inline-flex items-center gap-1.5" v-tooltip.top="data.addresses.join(', ')">
                <span class="font-mono text-surface-800 dark:text-surface-100">{{ primaryAddress(data.addresses) }}</span>
                <span v-if="data.addresses.length > 1" class="text-xs text-muted-color">+{{ data.addresses.length - 1 }}</span>
              </span>
            </template>
          </Column>
          <Column style="width: 13rem">
            <template #body="{ data }">
              <div class="flex justify-end">
                <Button v-if="data.telemetryServiceName" label="Logs" icon="pi pi-align-left" severity="secondary" text
                        size="small" @click="openServerLogs(data)" />
                <Button label="Logging" icon="pi pi-sliders-h" severity="secondary" text size="small"
                        @click="openLogLevel(data.nodeId)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </DashboardSection>

      <div class="grid gap-4 lg:grid-cols-2">
        <DashboardSection :icon="Activity" :tint="TINTS.ink" title="Platform observability"
                          description="Traces and metrics of the platform's own servers, in the system tenant.">
          <div class="flex flex-col gap-4 p-5">
            <div class="flex flex-wrap gap-2">
              <span v-for="signal in SIGNALS" :key="signal.label"
                    class="inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-1 text-xs font-medium text-surface-700 dark:bg-surface-800 dark:text-surface-200">
                <component :is="signal.icon" :size="13" :stroke-width="1.75" aria-hidden="true" />{{ signal.label }}
              </span>
            </div>
            <div>
              <Button label="Open observability" icon="pi pi-chart-line" severity="secondary" outlined size="small"
                      @click="router.push('/observability')" />
            </div>
          </div>
        </DashboardSection>
        <DashboardSection :icon="Boxes" :tint="TINTS.ink" title="Platform workloads"
                          description="Workloads the platform runs for itself, with no organization.">
          <div class="flex flex-col gap-4 p-5">
            <div v-if="platformWorkloads !== null">
              <div class="flex items-baseline justify-between gap-3 text-sm">
                <span><b class="text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ platformRunning }}</b>
                  <span class="text-muted-color"> of {{ platformWorkloads.length }} running</span></span>
                <span class="flex items-center gap-3 text-xs text-muted-color">
                  <span class="flex items-center gap-1.5"><span class="h-2 w-2 rounded-full bg-green-500" aria-hidden="true" />{{ platformRunning }} running</span>
                  <span v-if="platformFailed > 0" class="flex items-center gap-1.5"><span class="h-2 w-2 rounded-full bg-red-500" aria-hidden="true" />{{ platformFailed }} failed</span>
                </span>
              </div>
              <div class="mt-2 flex h-1.5 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                <span class="h-full bg-green-500" :style="{ width: `${share(platformRunning)}%` }" />
                <span class="h-full bg-red-500" :style="{ width: `${share(platformFailed)}%` }" />
              </div>
            </div>
            <div>
              <Button label="Show platform workloads" icon="pi pi-box" severity="secondary" outlined size="small"
                      @click="router.push({ path: '/workloads', query: { org: PLATFORM_ONLY } })" />
            </div>
          </div>
        </DashboardSection>
      </div>
    </div>

    <LogLevelDialog v-if="logLevelNodeId" v-model:visible="logLevelVisible" :node-id="logLevelNodeId" />
    <ServerLogsDialog v-if="serverLogsNode?.telemetryServiceName" v-model:visible="serverLogsVisible"
                      :telemetry-service-name="serverLogsNode.telemetryServiceName"
                      :telemetry-service-instance-id="serverLogsNode.telemetryServiceInstanceId" />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Activity, Boxes, ChartGantt, ChartLine, Network, ScrollText, Server, Tag as TagIcon } from '@lucide/vue'

import { Kinotic } from '@kinotic-ai/core'
import type { KinoticClusterInfo, KinoticNodeInfo } from '@kinotic-ai/system-api'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { DashboardSection, HeartbeatIcon, NoAccessState, PageHeader, StatCard, TINTS, errorMessage, EmptyChartCharacter, HEARTBEAT_TINTS, HeartbeatState, isAuthorizationError } from '@kinotic-ai/frontend-common'

import LogLevelDialog from '@/components/LogLevelDialog.vue'
import ServerLogsDialog from '@/components/ServerLogsDialog.vue'
import { PLATFORM_ONLY, scanWorkloads } from '@/util/workloads'
import { clusterHeartbeat as clusterHeartbeatOf } from '@/util/nodes'

const router = useRouter()

const cluster = ref<KinoticClusterInfo | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const refused = ref(false)

const logLevelNodeId = ref<string | null>(null)
const logLevelVisible = ref(false)

const serverLogsNode = ref<KinoticNodeInfo | null>(null)
const serverLogsVisible = ref(false)

/** What a node running from classes, with no packaged version, shows for one. */
const UNKNOWN_VERSION = 'unknown'

/** What the platform's observability holds, named on its card. */
const SIGNALS = [
  { label: 'Traces', icon: markRaw(ChartGantt) },
  { label: 'Metrics', icon: markRaw(ChartLine) },
  { label: 'Server logs', icon: markRaw(ScrollText) }
]

const orderedNodes = computed(() => [...(cluster.value?.nodes ?? [])].sort((a, b) => a.order - b.order))

/** The platform's own workloads, null until they load or when they fail to. */
const platformWorkloads = ref<Workload[] | null>(null)
const platformRunning = computed(() => platformWorkloads.value?.filter(w => w.status === WorkloadStatus.RUNNING).length ?? 0)
const platformFailed = computed(() => platformWorkloads.value?.filter(w => w.status === WorkloadStatus.FAILED).length ?? 0)

function share(count: number): number {
  const total = platformWorkloads.value?.length ?? 0
  return total > 0 ? Math.round((count / total) * 100) : 0
}

/** How a node reads in the list: its server's name, else its first host name, else its short id. */
function nodeTitle(node: KinoticNodeInfo): string {
  return node.serverName || node.hostNames[0] || shortId(node.nodeId)
}

/** The release a version string names, without its build suffix: "2.18.0" of "2.18.0#20260423-sha1:d49adada". */
function releaseOf(version: string | null): string {
  return version?.split('#')[0] || UNKNOWN_VERSION
}

function shortId(id: string): string {
  return id.split('-')[0] ?? id
}

/** The address other machines reach the node on: the first that is not a loopback. */
function primaryAddress(addresses: string[]): string {
  const external = addresses.find(address => !address.startsWith('127.') && !address.includes('%lo') && address !== '::1')
  return external ?? addresses[0] ?? '—'
}

// The version most nodes run; a node on another is behind a stalled rolling upgrade
const commonVersion = computed<string | null>(() => {
  const byVersion = new Map<string | null, number>()
  for (const node of cluster.value?.nodes ?? []) {
    byVersion.set(node.version, (byVersion.get(node.version) ?? 0) + 1)
  }
  return [...byVersion.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null
})

const mixedVersions = computed(() => new Set((cluster.value?.nodes ?? []).map(node => node.version)).size > 1)

interface Stat {
  label: string
  value: string
  detail: string
  /** Renders the value as a Tag of this severity instead of a number. */
  tag?: string
  icon?: Component
  /** Shows a HeartbeatIcon in this state in place of the icon. */
  heartbeat?: HeartbeatState
  /** One of TINTS. */
  tint: string
}

const clusterHeartbeat = computed(() => clusterHeartbeatOf(cluster.value))

const stats = computed<Stat[]>(() => [
  {
    label: 'Cluster state',
    value: cluster.value?.clusterState ?? '—',
    detail: 'Whether the cluster is serving requests',
    tag: cluster.value ? (cluster.value.active ? 'success' : 'danger') : 'secondary',
    heartbeat: clusterHeartbeat.value,
    tint: HEARTBEAT_TINTS[clusterHeartbeat.value]
  },
  {
    label: 'Server nodes',
    value: cluster.value?.serverNodeCount?.toString() ?? '—',
    detail: 'Org, system and app server nodes in the cluster',
    icon: markRaw(Server),
    tint: TINTS.ink
  },
  {
    label: 'Topology version',
    value: cluster.value?.topologyVersion?.toString() ?? '—',
    detail: 'Increments each time a node joins or leaves',
    icon: markRaw(Network),
    tint: TINTS.ink
  },
  versionStat.value
])

const versionStat = computed<Stat>(() => {
  let ret: Stat
  if (mixedVersions.value) {
    ret = {
      label: 'Versions',
      value: 'Mixed',
      detail: 'Not every node runs the same version',
      tag: 'warn',
      icon: markRaw(TagIcon),
      tint: TINTS.ink
    }
  } else {
    ret = {
      label: 'Version',
      value: cluster.value?.nodes.length ? releaseOf(commonVersion.value) : '—',
      detail: commonVersion.value?.includes('#') ? `Every node runs build ${commonVersion.value.split('#')[1]}` : 'The Kinotic version every node runs',
      icon: markRaw(TagIcon),
      tint: TINTS.ink
    }
  }
  return ret
})

function openServerLogs(node: KinoticNodeInfo) {
  serverLogsNode.value = node
  serverLogsVisible.value = true
}

function openLogLevel(nodeId: string) {
  logLevelNodeId.value = nodeId
  logLevelVisible.value = true
}

async function load() {
  loading.value = true
  error.value = null
  // the platform workloads summary is a nicety; failing to load it leaves its card with just the link
  scanWorkloads({}, { platformOnly: true }).then(list => { platformWorkloads.value = list })
                                          .catch(() => { platformWorkloads.value = null })
  try {
    cluster.value = await Kinotic.clusterInfo.getClusterInfo()
    refused.value = false
  } catch (err) {
    refused.value = isAuthorizationError(err)
    error.value = refused.value ? null : errorMessage(err, 'Failed to load cluster info')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* the canvas: faint dots under a soft purple wash at either edge and a glow through the middle */
.topology {
  background-image:
    linear-gradient(90deg, rgb(168 85 247 / 0.12), transparent 30%, transparent 70%, rgb(168 85 247 / 0.12)),
    radial-gradient(ellipse 35% 120% at 50% 50%, rgb(168 85 247 / 0.09), transparent 100%),
    radial-gradient(circle, var(--p-surface-200) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 14px 14px;
}

/* the streak crosses each link in turn, fading in and out at its ends */
.topology__pulse {
  animation: topology-pulse 2.4s ease-in-out infinite;
}

@keyframes topology-pulse {
  0% { left: -4rem; opacity: 0; }
  20% { opacity: 1; }
  80% { opacity: 1; }
  100% { left: calc(100% - 0.75rem); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .topology__pulse {
    display: none;
  }
}
</style>

<style>
/* the dark canvas lives outside the scoped block so it matches the .dark class on <html> */
.dark .topology {
  background-image:
    linear-gradient(90deg, rgb(168 85 247 / 0.18), transparent 30%, transparent 70%, rgb(168 85 247 / 0.18)),
    radial-gradient(ellipse 35% 120% at 50% 50%, rgb(168 85 247 / 0.14), transparent 100%),
    radial-gradient(circle, var(--p-surface-800) 1px, transparent 1px);
}
</style>
