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

      <DashboardSection :icon="Server" :tint="TINTS.sky" title="Server nodes" :count="cluster?.nodes.length"
                        description="Logs follows that node's logs. Logging opens its logger levels and trace-log filters.">
        <DataTable :value="cluster?.nodes ?? []" size="small" class="text-sm" data-key="nodeId">
          <template #empty>
            <div v-if="loading" class="py-6 text-center text-sm text-muted-color">Loading cluster topology…</div>
            <EmptyChartCharacter v-else class="py-6" title="No server nodes reported" />
          </template>
          <Column field="serverName" header="Server" />
          <Column header="Node">
            <template #body="{ data }">
              <span class="flex items-center gap-2.5">
                <span :class="['flex h-7 w-7 shrink-0 items-center justify-center rounded-md', TINTS.sky]">
                  <Server :size="14" :stroke-width="1.75" aria-hidden="true" />
                </span>
                <span class="font-mono text-xs">{{ data.nodeId }}</span>
              </span>
            </template>
          </Column>
          <Column header="Version">
            <template #body="{ data }">
              {{ data.version ?? UNKNOWN_VERSION }}
              <Tag v-if="commonVersion && data.version !== commonVersion" value="behind" severity="warn" class="ml-1" />
            </template>
          </Column>
          <Column field="order" header="Join order" class="hidden md:table-cell" />
          <Column header="Addresses" class="hidden md:table-cell">
            <template #body="{ data }"><span class="font-mono text-xs">{{ data.addresses.join(', ') }}</span></template>
          </Column>
          <Column header="Host names" class="hidden md:table-cell">
            <template #body="{ data }"><span class="font-mono text-xs">{{ data.hostNames.join(', ') }}</span></template>
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
        <DashboardSection :icon="Activity" :tint="TINTS.purple" title="Platform observability"
                          description="Traces and metrics of the servers themselves live in the system tenant, the same one the workload log and telemetry queries fall back to for a platform operator.">
          <div class="p-5">
            <Button label="Open observability" icon="pi pi-chart-line" severity="secondary" outlined size="small"
                    @click="router.push('/observability')" />
          </div>
        </DashboardSection>
        <DashboardSection :icon="Boxes" :tint="TINTS.green" title="Platform workloads"
                          description="Workloads the platform runs for itself, with no organization.">
          <div class="p-5">
            <Button label="Show platform workloads" icon="pi pi-box" severity="secondary" outlined size="small"
                    @click="router.push({ path: '/workloads', query: { org: PLATFORM_ONLY } })" />
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
import { Activity, Boxes, Network, Server, Tag as TagIcon } from '@lucide/vue'

import { Kinotic } from '@kinotic-ai/core'
import type { KinoticClusterInfo, KinoticNodeInfo } from '@kinotic-ai/system-api'
import { DashboardSection, HeartbeatIcon, PageHeader, StatCard, TINTS, errorMessage, EmptyChartCharacter, HEARTBEAT_TINTS, HeartbeatState } from '@kinotic-ai/frontend-common'

import LogLevelDialog from '@/components/LogLevelDialog.vue'
import ServerLogsDialog from '@/components/ServerLogsDialog.vue'
import { PLATFORM_ONLY } from '@/util/workloads'
import { clusterHeartbeat as clusterHeartbeatOf } from '@/util/nodes'

const router = useRouter()

const cluster = ref<KinoticClusterInfo | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const logLevelNodeId = ref<string | null>(null)
const logLevelVisible = ref(false)

const serverLogsNode = ref<KinoticNodeInfo | null>(null)
const serverLogsVisible = ref(false)

/** What a node running from classes, with no packaged version, shows for one. */
const UNKNOWN_VERSION = 'unknown'

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
    tint: TINTS.sky
  },
  {
    label: 'Topology version',
    value: cluster.value?.topologyVersion?.toString() ?? '—',
    detail: 'Increments each time a node joins or leaves',
    icon: markRaw(Network),
    tint: TINTS.purple
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
      tint: TINTS.orange
    }
  } else {
    ret = {
      label: 'Version',
      value: cluster.value?.nodes.length ? (commonVersion.value ?? UNKNOWN_VERSION) : '—',
      detail: 'The Kinotic version every node runs',
      icon: markRaw(TagIcon),
      tint: TINTS.blue
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
