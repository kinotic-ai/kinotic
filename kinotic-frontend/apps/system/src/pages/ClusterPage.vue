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
          <template v-if="stat.alive !== undefined" #icon>
            <HeartbeatIcon :alive="stat.alive" :size="20" :stroke-width="1.75" />
          </template>
          <template v-if="stat.tag" #default>
            <Tag :value="stat.value" :severity="stat.tag" />
          </template>
        </StatCard>
      </div>

      <DashboardSection :icon="Server" :tint="TINTS.sky" title="Server nodes" :count="cluster?.nodes.length"
                        description="One node serves this console's connection. Logging opens that node's logger levels and trace-log filters.">
        <DataTable :value="cluster?.nodes ?? []" size="small" class="text-sm" data-key="nodeId">
          <template #empty>
            <div class="py-6 text-center text-sm text-muted-color">{{ loading ? 'Loading cluster topology…' : 'No server nodes reported' }}</div>
          </template>
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
              {{ splitVersion(data.version).release }}
              <span v-if="splitVersion(data.version).build" class="font-mono text-xs text-muted-color">{{ splitVersion(data.version).build }}</span>
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
          <Column>
            <template #body="{ data }"><Tag v-if="data.local" severity="info" value="serving request" /></template>
          </Column>
          <Column style="width: 8rem">
            <template #body="{ data }">
              <div class="text-right">
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
import type { KinoticClusterInfo } from '@kinotic-ai/system-api'
import { DashboardSection, HeartbeatIcon, PageHeader, StatCard, TINTS, errorMessage } from '@kinotic-ai/frontend-common'

import LogLevelDialog from '@/components/LogLevelDialog.vue'
import { PLATFORM_ONLY } from '@/util/workloads'

const router = useRouter()

const cluster = ref<KinoticClusterInfo | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const logLevelNodeId = ref<string | null>(null)
const logLevelVisible = ref(false)

// The build most nodes run; a node on another build is behind a stalled rolling upgrade
const commonVersion = computed<string | null>(() => {
  const byVersion = new Map<string, number>()
  for (const node of cluster.value?.nodes ?? []) {
    byVersion.set(node.version, (byVersion.get(node.version) ?? 0) + 1)
  }
  return [...byVersion.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null
})

const mixedVersions = computed(() => new Set((cluster.value?.nodes ?? []).map(node => node.version)).size > 1)

/** A node version as the release it is and the build stamp after the '#', e.g. 2.18.0 and 20260423-sha1:d49adada. */
function splitVersion(version: string): { release: string; build: string | null } {
  const at = version.indexOf('#')
  return at < 0 ? { release: version, build: null } : { release: version.slice(0, at), build: version.slice(at + 1) }
}

interface Stat {
  label: string
  value: string
  detail: string
  /** Renders the value as a Tag of this severity instead of a number. */
  tag?: string
  icon?: Component
  /** Shows a HeartbeatIcon in place of the icon: beating while true, flat while false. */
  alive?: boolean
  /** One of TINTS. */
  tint: string
}

const stats = computed<Stat[]>(() => [
  {
    label: 'Cluster state',
    value: cluster.value?.clusterState ?? '—',
    detail: 'Whether the cluster is serving requests',
    tag: cluster.value ? (cluster.value.active ? 'success' : 'danger') : 'secondary',
    alive: cluster.value?.active ?? false,
    tint: cluster.value?.active ? TINTS.purple : TINTS.surface
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

// The release reads at display size; the build stamp behind the '#' goes in the detail
const versionStat = computed<Stat>(() => {
  let ret: Stat
  if (mixedVersions.value) {
    ret = {
      label: 'Versions',
      value: 'Mixed',
      detail: 'Not every node runs the same build',
      tag: 'warn',
      icon: markRaw(TagIcon),
      tint: TINTS.orange
    }
  } else {
    const common = commonVersion.value ? splitVersion(commonVersion.value) : null
    ret = {
      label: 'Version',
      value: common?.release ?? '—',
      detail: common?.build ? `build ${common.build} · every node runs it` : 'Every node runs this build',
      icon: markRaw(TagIcon),
      tint: TINTS.blue
    }
  }
  return ret
})

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
