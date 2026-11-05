<template>
  <div>
    <PageHeader title="Cluster" description="The org, system and app server nodes and how they are doing as a group.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="flex flex-col gap-4">
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

      <DashboardSection :icon="ShieldAlert" :tint="advisoryRows.length ? TINTS.red : TINTS.ink" title="Vulnerabilities"
                        :count="advisoryRows.length"
                        description="Advisories from OSV that affect the libraries the running server builds were built with. Nodes running the same build share one entry.">
        <Message v-if="vulnerabilityError" severity="warn" :closable="false" class="m-5">{{ vulnerabilityError }}</Message>
        <div v-else-if="servers === null" class="py-6 text-center text-sm text-muted-color">Checking the servers' libraries…</div>
        <template v-else>
          <div class="flex flex-wrap gap-2 border-b border-surface-200 px-5 py-3 dark:border-surface-700">
            <span v-for="(server, index) in servers" :key="index" v-tooltip.top="buildProblem(server)"
                  class="inline-flex items-center gap-1.5 rounded-md border border-surface-200 px-2 py-1 text-xs dark:border-surface-700">
              <span :class="['size-2 rounded-full', BUILD_DOT[buildState(server)]]" />
              <span class="font-semibold text-surface-950 dark:text-surface-0">{{ server.serverName || UNKNOWN_VERSION }}</span>
              <span class="font-mono text-surface-700 dark:text-surface-200">{{ releaseOf(server.version) }}</span>
              <span class="text-muted-color">· {{ server.nodeCount }} {{ server.nodeCount === 1 ? 'node' : 'nodes' }} · {{ buildSummary(server) }}</span>
            </span>
          </div>
          <VulnerabilityFindingsTable :items="advisoryRows" data-key="finding.advisoryId"
                                      empty-hint="No advisory affects the libraries of the running server builds.">
            <template #columns>
              <Column header="Builds" style="width: 18%">
                <template #body="{ data }">
                  <div class="flex flex-wrap gap-1">
                    <span v-for="build in data.builds" :key="`${build.serverName}@${build.version}`"
                          class="rounded-md bg-surface-100 px-1.5 py-0.5 font-mono text-xs text-surface-700 dark:bg-surface-800 dark:text-surface-200">{{ build.serverName || UNKNOWN_VERSION }} {{ releaseOf(build.version) }}</span>
                  </div>
                </template>
              </Column>
            </template>
          </VulnerabilityFindingsTable>
        </template>
      </DashboardSection>

      <DashboardSection :icon="Server" :tint="TINTS.ink" title="Server nodes" :count="cluster?.nodes.length"
                        description="Each server and how many nodes run it; select one to list its nodes. Logs follows a node's logs. Logging opens its logger levels and trace-log filters.">
        <!-- the cluster's shape at a glance: its servers in the order their first node joined, linked,
             with a pulse running between them while the cluster serves -->
        <div v-if="serverGroups.length > 0"
             class="topology flex items-center gap-0 overflow-x-auto border-b border-surface-200 px-5 py-5 dark:border-surface-700">
          <template v-for="(group, position) in serverGroups" :key="group.name">
            <div v-if="position > 0" class="relative h-px min-w-8 flex-1 overflow-visible bg-surface-300 dark:bg-surface-600" aria-hidden="true">
              <!-- the pulse: a streak of light fading out at both ends, its bright head leading -->
              <span v-if="cluster?.active" class="topology__pulse absolute -top-[2px] flex h-[5px] w-16 items-center">
                <span class="h-px flex-1 bg-gradient-to-r from-transparent via-purple-400 to-purple-500" />
                <span class="h-[5px] w-[5px] shrink-0 rounded-full bg-purple-500 shadow-[0_0_6px_rgb(168_85_247/0.8)]" />
                <span class="h-px w-3 bg-gradient-to-r from-purple-500 to-transparent" />
              </span>
            </div>
            <button type="button" :aria-pressed="serverFilter === group.name" @click="toggleServerFilter(group.name)"
                    :class="['flex shrink-0 items-center gap-2.5 rounded-xl border bg-surface-0 px-3 py-2 text-left shadow-sm dark:bg-surface-900',
                             serverFilter === group.name ? 'border-purple-500 ring-2 ring-purple-500/30' : 'border-surface-200 dark:border-surface-700']">
              <span :class="['flex h-7 w-7 items-center justify-center rounded-md', TINTS.ink]">
                <Server :size="14" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <div class="leading-tight">
                <div class="text-[0.8125rem] font-semibold text-surface-950 dark:text-surface-0">{{ group.name }}</div>
                <div class="text-[0.6875rem] text-muted-color">
                  {{ group.nodes.length }} {{ group.nodes.length === 1 ? 'node' : 'nodes' }}<span v-if="group.behind" class="text-orange-600 dark:text-orange-400"> · {{ group.behind }} behind</span>
                </div>
              </div>
            </button>
          </template>
        </div>
        <template v-if="orderedNodes.length === 0">
          <div v-if="loading" class="py-6 text-center text-sm text-muted-color">Loading cluster topology…</div>
          <EmptyChartCharacter v-else class="py-6" title="No server nodes reported" />
        </template>
        <p v-else-if="serverFilter === null" class="px-5 py-4 text-sm text-muted-color">Select a server above to list its nodes.</p>
        <template v-else>
          <div class="flex flex-wrap items-center gap-3 border-b border-surface-200 px-5 py-3 dark:border-surface-700">
            <IconField class="w-full sm:w-80">
              <InputIcon class="pi pi-search" />
              <InputText v-model="nodeSearch" placeholder="Search by name, address or id" size="small" class="w-full" />
            </IconField>
            <Button :label="`${serverFilter} nodes`" icon="pi pi-times" icon-pos="right" severity="secondary"
                    size="small" outlined v-tooltip.top="'Close the node list'" @click="serverFilter = null" />
          </div>
          <DataTable :value="pageNodes" size="small" class="text-sm" data-key="nodeId">
            <template #empty>
              <div class="py-6 text-center text-sm text-muted-color">No {{ serverFilter }} node matches the search.</div>
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
          <TablePaginator v-if="visibleNodes.length" class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                          :first="nodeFirst" :rows="nodePageSize" :total-records="visibleNodes.length"
                          :rows-per-page-options="NODE_PAGE_SIZES" @page="onNodePage" />
        </template>
      </DashboardSection>

      <div class="grid gap-4 lg:grid-cols-2">
        <DashboardSection :icon="Activity" :tint="TINTS.ink" title="System observability"
                          description="Traces and metrics of the system's own servers, in the system tenant.">
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
        <DashboardSection :icon="Boxes" :tint="TINTS.ink" title="System workloads"
                          description="Workloads the system runs for itself, with no organization.">
          <div class="flex flex-col gap-4 p-5">
            <div v-if="systemWorkloads !== null">
              <div class="flex items-baseline justify-between gap-3 text-sm">
                <span><b class="text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ systemRunning }}</b>
                  <span class="text-muted-color"> of {{ systemWorkloads.length }} running</span></span>
                <span class="flex items-center gap-3 text-xs text-muted-color">
                  <span class="flex items-center gap-1.5"><span class="h-2 w-2 rounded-full bg-green-500" aria-hidden="true" />{{ systemRunning }} running</span>
                  <span v-if="systemFailed > 0" class="flex items-center gap-1.5"><span class="h-2 w-2 rounded-full bg-red-500" aria-hidden="true" />{{ systemFailed }} failed</span>
                </span>
              </div>
              <div class="mt-2 flex h-1.5 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                <span class="h-full bg-green-500" :style="{ width: `${share(systemRunning)}%` }" />
                <span class="h-full bg-red-500" :style="{ width: `${share(systemFailed)}%` }" />
              </div>
            </div>
            <div>
              <Button label="Show system workloads" icon="pi pi-box" severity="secondary" outlined size="small"
                      @click="router.push({ path: '/workloads', query: { org: SYSTEM_ONLY } })" />
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
import { computed, markRaw, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import type { PageState } from 'primevue/paginator'
import Tag from 'primevue/tag'
import { Activity, Boxes, ChartGantt, ChartLine, Network, ScrollText, Server, ShieldAlert, Tag as TagIcon } from '@lucide/vue'

import { Kinotic } from '@kinotic-ai/core'
import type { KinoticClusterInfo, KinoticNodeInfo, ServerVulnerabilities } from '@kinotic-ai/system-api'
import { AdvisorySeverity, AdvisoryType, WorkloadStatus, type VulnerabilityFinding, type Workload } from '@kinotic-ai/management-api'
import { DashboardSection, HeartbeatIcon, PageHeader, StatCard, TINTS, errorMessage, EmptyChartCharacter, HEARTBEAT_TINTS, HeartbeatState, TablePaginator } from '@kinotic-ai/frontend-common'
import { countSevere, VulnerabilityFindingsTable } from '@kinotic-ai/frontend-common/management'

import LogLevelDialog from '@/components/LogLevelDialog.vue'
import ServerLogsDialog from '@/components/ServerLogsDialog.vue'
import { SYSTEM_ONLY, scanWorkloads } from '@/util/workloads'
import { clusterHeartbeat as clusterHeartbeatOf } from '@/util/nodes'

const router = useRouter()

const cluster = ref<KinoticClusterInfo | null>(null)
/** Each running server build with the advisories affecting its libraries, null until they load. */
const servers = ref<ServerVulnerabilities[] | null>(null)
const vulnerabilityError = ref<string | null>(null)

/** How a build's libraries checked out, keying the colour of its dot. */
enum BuildState { SEVERE, AFFECTED, CLEAN, UNCHECKED }

const BUILD_DOT: Record<BuildState, string> = {
  [BuildState.SEVERE]: 'bg-red-500',
  [BuildState.AFFECTED]: 'bg-orange-400',
  [BuildState.CLEAN]: 'bg-green-500',
  [BuildState.UNCHECKED]: 'bg-surface-400',
}

/** One row per advisory, with every running build it affects and the affected versions of all of them, the most severe first. */
const advisoryRows = computed(() => {
  const byId = new Map<string, { finding: VulnerabilityFinding, builds: ServerVulnerabilities[] }>()
  for (const server of servers.value ?? []) {
    for (const finding of server.findings) {
      const row = byId.get(finding.advisoryId)
      if (row) {
        row.builds.push(server)
        const known = new Set(row.finding.packages.map(pkg => pkg.purl))
        row.finding.packages.push(...finding.packages.filter(pkg => !known.has(pkg.purl)))
      } else {
        byId.set(finding.advisoryId, { finding: { ...finding, packages: [...finding.packages] }, builds: [server] })
      }
    }
  }
  return [...byId.values()].sort((a, b) => severityRank(a.finding) - severityRank(b.finding))
})
const loading = ref(false)
const error = ref<string | null>(null)

const logLevelNodeId = ref<string | null>(null)
const logLevelVisible = ref(false)

const serverLogsNode = ref<KinoticNodeInfo | null>(null)
const serverLogsVisible = ref(false)

/** What a node running from classes, with no packaged version, shows for one. */
const UNKNOWN_VERSION = 'unknown'

const SEVERITY_ORDER = Object.values(AdvisorySeverity)

/** What the system's observability holds, named on its card. */
const SIGNALS = [
  { label: 'Traces', icon: markRaw(ChartGantt) },
  { label: 'Metrics', icon: markRaw(ChartLine) },
  { label: 'Server logs', icon: markRaw(ScrollText) }
]

const orderedNodes = computed(() => [...(cluster.value?.nodes ?? [])].sort((a, b) => a.order - b.order))

const NODE_PAGE_SIZES = [10, 25, 50, 100]

/** The server, by name, whose nodes the node list shows, or null while no server is selected. */
const serverFilter = ref<string | null>(null)
const nodeSearch = ref('')
const nodeFirst = ref(0)
const nodePageSize = ref(NODE_PAGE_SIZES[0])

/** The cluster's nodes grouped by the server they run, in the order each server's first node joined. */
const serverGroups = computed(() => {
  const byName = new Map<string, { name: string, nodes: KinoticNodeInfo[], behind: number }>()
  for (const node of orderedNodes.value) {
    const name = serverOf(node)
    let group = byName.get(name)
    if (!group) {
      group = { name, nodes: [], behind: 0 }
      byName.set(name, group)
    }
    group.nodes.push(node)
    if (commonVersion.value && node.version !== commonVersion.value) {
      group.behind++
    }
  }
  return [...byName.values()]
})

const visibleNodes = computed(() => {
  const needle = nodeSearch.value.trim().toLowerCase()
  return orderedNodes.value.filter(node => (serverFilter.value === null || serverOf(node) === serverFilter.value)
    && (!needle || [nodeTitle(node), node.nodeId, ...node.addresses, ...node.hostNames].some(text => text.toLowerCase().includes(needle))))
})

const pageNodes = computed(() => visibleNodes.value.slice(nodeFirst.value, nodeFirst.value + nodePageSize.value))

watch([nodeSearch, serverFilter], () => {
  nodeFirst.value = 0
})

/** The system's own workloads, null until they load or when they fail to. */
const systemWorkloads = ref<Workload[] | null>(null)
const systemRunning = computed(() => systemWorkloads.value?.filter(w => w.status === WorkloadStatus.RUNNING).length ?? 0)
const systemFailed = computed(() => systemWorkloads.value?.filter(w => w.status === WorkloadStatus.FAILED).length ?? 0)

function share(count: number): number {
  const total = systemWorkloads.value?.length ?? 0
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

// a malicious package ranks above every severity
function severityRank(finding: VulnerabilityFinding): number {
  return finding.type === AdvisoryType.MALICIOUS ? -1 : SEVERITY_ORDER.indexOf(finding.severity)
}

function buildState(server: ServerVulnerabilities): BuildState {
  let ret: BuildState
  if (server.error || !server.componentCount) {
    ret = BuildState.UNCHECKED
  } else if (countSevere(server.findings)) {
    ret = BuildState.SEVERE
  } else if (server.findings.length) {
    ret = BuildState.AFFECTED
  } else {
    ret = BuildState.CLEAN
  }
  return ret
}

/** What a build's chip reads after its node count. */
function buildSummary(server: ServerVulnerabilities): string {
  let ret: string
  if (server.error) {
    ret = 'not checked'
  } else if (!server.componentCount) {
    ret = 'no library list'
  } else if (server.findings.length) {
    ret = `${server.findings.length} ${server.findings.length === 1 ? 'advisory' : 'advisories'}`
  } else {
    ret = 'no advisories'
  }
  return ret
}

/** Why a build's libraries were not checked, shown on hovering its chip. */
function buildProblem(server: ServerVulnerabilities): string | undefined {
  let ret: string | undefined
  if (server.error) {
    ret = `This build's libraries could not be checked: ${server.error}`
  } else if (!server.componentCount) {
    ret = 'This build was started without the library list a server build packages, so there are no libraries to check.'
  }
  return ret
}

// a node that names no server is grouped under its own title, so it never merges with another
function serverOf(node: KinoticNodeInfo): string {
  return node.serverName || nodeTitle(node)
}

function toggleServerFilter(name: string): void {
  serverFilter.value = serverFilter.value === name ? null : name
}

function onNodePage(state: PageState): void {
  nodeFirst.value = state.first
  nodePageSize.value = state.rows
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
  // the system workloads summary is a nicety; failing to load it leaves its card with just the link
  scanWorkloads({}, { systemOnly: true }).then(list => { systemWorkloads.value = list })
                                          .catch(() => { systemWorkloads.value = null })
  // the libraries are checked apart from the topology, which shows without waiting for them
  Kinotic.clusterVulnerabilities.findAll().then(found => {
    servers.value = found
    vulnerabilityError.value = null
  }).catch(err => {
    vulnerabilityError.value = errorMessage(err, "The servers' libraries could not be checked")
  })
  try {
    cluster.value = await Kinotic.clusterInfo.getClusterInfo()
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load cluster info')
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
