<template>
  <div class="flex flex-col">
    <PageHeader :title="workload?.name ?? workloadId">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Workloads</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate">{{ workload?.name ?? workloadId }}</span>
      </template>
      <template #status>
        <Tag v-if="workload" :value="workload.status" :severity="workloadSeverity(workload.status)" />
        <NodeUnreachableNote v-if="unreachable" :message="unreachable.message" />
      </template>
      <template #actions>
        <Button v-if="canStop" label="Stop" icon="pi pi-stop-circle" severity="secondary" outlined
                @click="act(() => Kinotic.workloadOrchestration.stopWorkload(workloadId), 'Workload stopping', 'Failed to stop workload')" />
        <Button v-if="canDestroy" label="Destroy" icon="pi pi-power-off" severity="danger" outlined @click="confirmDestroy" />
        <Button v-else label="Delete" icon="pi pi-trash" severity="danger" outlined :disabled="!workload" @click="confirmDelete" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <Tabs v-model:value="tab">
      <TabList>
        <Tab value="overview"><span class="flex items-center gap-2"><LayoutDashboard :size="18" :stroke-width="1.75" aria-hidden="true" />Overview</span></Tab>
        <Tab value="logs"><span class="flex items-center gap-2"><ScrollText :size="18" :stroke-width="1.75" aria-hidden="true" />Logs</span></Tab>
        <Tab value="history"><span class="flex items-center gap-2"><Clock :size="18" :stroke-width="1.75" aria-hidden="true" />History</span></Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="overview">
          <div v-if="workload" class="flex flex-col gap-4 pt-2">
            <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
              <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                        :value="stat.tag ? undefined : stat.value" :detail="stat.detail" :to="stat.to">
                <template v-if="stat.heartbeat !== undefined" #icon>
                  <HeartbeatIcon :state="stat.heartbeat" :size="20" :stroke-width="1.75" />
                </template>
                <template v-if="stat.tag" #default>
                  <Tag :value="stat.value" :severity="stat.tag" />
                </template>
              </StatCard>
            </div>

            <Message v-if="unreachable" severity="warn" :closable="false">
              {{ unreachable.message }}, {{ DatetimeUtil.formatRelativeDate(unreachable.since).toLowerCase() }}. The status is the last the node
              reported; the VM may still be running, so its room stays held. The node's next report of the workload settles it.
            </Message>
            <Message v-if="workload.status === WorkloadStatus.FAILED" severity="error" :closable="false">
              The VM exited{{ workload.exitCode !== null ? ` with code ${workload.exitCode}` : '' }}. Its last log lines are on the Logs tab.
            </Message>

            <div class="grid gap-4 lg:grid-cols-2">
              <DashboardSection :icon="Terminal" :tint="tint" title="Runtime">
                <div class="px-5 pb-3">
                  <FactList :facts="runtimeFacts" />
                </div>
              </DashboardSection>

              <DashboardSection :icon="Network" :tint="tint" title="Network"
                                description="Every destination other than the allowed hosts is blocked.">
                <div class="p-5">
                  <div class="mb-1 text-xs font-medium uppercase tracking-wide text-muted-color">Allowed hosts</div>
                  <div v-if="workload.network?.mode === NetworkMode.DISABLED" class="text-sm text-muted-color">Networking is disabled for this VM.</div>
                  <div v-else-if="allowedHosts.length === 0" class="text-sm text-muted-color">No host is allowed; the node adds the resolver and, for telemetry, its own OTLP endpoint.</div>
                  <div v-else class="flex flex-wrap gap-1.5">
                    <span v-for="host in allowedHosts" :key="host" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ host }}</span>
                  </div>
                  <div class="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-muted-color">Ports</div>
                  <div v-if="ports.length === 0" class="text-sm text-muted-color">None published</div>
                  <div v-else class="flex flex-wrap gap-1.5">
                    <span v-for="port in ports" :key="port" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ port }}</span>
                  </div>
                </div>
              </DashboardSection>

              <DashboardSection :icon="KeyRound" :tint="tint" title="Environment" :count="environmentNames.length"
                                description="Names only. Values and secrets are not shown.">
                <EmptyChartCharacter v-if="environmentNames.length === 0" class="py-6" title="No environment variables" />
                <div v-else class="flex flex-wrap gap-1.5 p-5">
                  <span v-for="name in environmentNames" :key="name" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ name }}</span>
                </div>
              </DashboardSection>

              <DashboardSection :icon="HardDrive" :tint="tint" title="Volumes" :count="volumes.length">
                <EmptyChartCharacter v-if="volumes.length === 0" class="py-6" title="No volume mounts" hint="The VM has its own disk only." />
                <div v-else class="flex flex-wrap gap-1.5 p-5">
                  <span v-for="volume in volumes" :key="volume" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ volume }}</span>
                </div>
              </DashboardSection>
            </div>
          </div>
          <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading workload…</div>
        </TabPanel>
        <TabPanel value="logs">
          <!-- Mounted with the tab, so a return starts a fresh history load and tail -->
          <WorkloadLogView v-if="tab === 'logs' && workload" :organization-id="workload.organizationId" :workload-id="workloadId" :run="workloadRun(workload)" class="pt-2" />
        </TabPanel>
        <TabPanel value="history">
          <div class="pt-2">
            <DashboardSection :icon="History" :tint="tint" title="History" :count="history.length"
                              :description="`What happened to the workload, newest first: each status its run passed through and each mark set beside it, with what caused it. The latest ${HISTORY_PAGE_SIZE} entries.`">
              <WatchEventsTimeline :entries="history" empty-text="Nothing has happened to the workload yet." />
            </DashboardSection>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { Building2, CalendarClock, CalendarPlus, Clock, Cpu, FileText, HardDrive, History, KeyRound, LayoutDashboard, LayoutGrid,
         Network, Package, Radio, Repeat, ScrollText, Server, Shield, Terminal } from '@lucide/vue'
import { computed, getCurrentInstance, markRaw, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { NetworkMode, WorkloadStatus, type WatchEvent, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DashboardSection, HeartbeatIcon, DatetimeUtil, FactList, PageHeader, StatCard, WatchEventsTimeline, WorkloadLogView, errorMessage,
         formatMb, showErrorToast, workloadRun, EmptyChartCharacter, HEARTBEAT_TINTS, HeartbeatState, NodeUnreachableNote } from '@kinotic-ai/frontend-common'

import { formatCpus, nodeHealth } from '@/util/nodes'
import { applicationPath, organizationPath, scopePath, scopeTint, type Scope } from '@/util/scope'
import { nodeUnreachable, runOpen, workloadSeverity } from '@/util/workloads'

/**
 * One workload, opened from a Workloads list: its state with the exit code, where it runs and
 * for whom, everything its record holds short of secret values, and its logs on a tab. The
 * eyebrow leads back to the list it was opened from.
 */
const props = defineProps<{
  workloadId: string
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const emit = defineEmits<{
  (e: 'deleted', workloadId: string): void
}>()

// Shown inside a list's drawer, the list handles a delete; on its own the page returns to the list
const embedded = !!getCurrentInstance()?.vnode.props?.onDeleted

const HISTORY_PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const listPath = computed(() => `${scopePath(scope.value)}/workloads`)
const tint = computed(() => scopeTint(scope.value))

const workload = ref<Workload | null>(null)
const node = ref<VmNode | null>(null)
const history = ref<WatchEvent[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// The tab lives in the URL so a row menu can open the logs directly
const tab = computed<string>({
  get: () => route.query.tab === 'logs' || route.query.tab === 'history' ? route.query.tab : 'overview',
  set: value => { router.replace({ query: { ...route.query, tab: value === 'overview' ? undefined : value } }) }
})

const canStop = computed(() => workload.value?.status === WorkloadStatus.RUNNING || workload.value?.status === WorkloadStatus.STARTING)
// A run holding a VM is destroyed; an ended one is deleted with its logs
const canDestroy = computed(() => workload.value !== null && runOpen(workload.value.status))
const unreachable = computed(() => workload.value ? nodeUnreachable(workload.value) : undefined)

const command = computed(() => [...(workload.value?.entrypoint ?? []), ...(workload.value?.cmd ?? [])].join(' '))
const allowedHosts = computed(() => workload.value?.network?.allowedHosts ?? [])
const ports = computed(() => (workload.value?.portMappings ?? []).map(port =>
    `${port.hostIp ? `${port.hostIp}:` : ''}${port.hostPort ?? port.guestPort}→${port.guestPort}/${(port.protocol ?? 'TCP').toLowerCase()}`))
const environmentNames = computed(() => Object.keys(workload.value?.environment ?? {}).sort())
const volumes = computed(() => (workload.value?.volumeMounts ?? []).map(volume =>
    `${volume.hostPath} → ${volume.guestPath}${volume.readOnly ? ' (ro)' : ''}`))

const runtimeFacts = computed(() => {
  const w = workload.value
  return [
    { label: 'Image', icon: markRaw(Package), value: w?.image ?? '—', mono: true },
    { label: 'Command', icon: markRaw(Terminal), value: command.value || '—', mono: true },
    { label: 'Detached', icon: markRaw(Repeat), value: w?.detached ? 'Yes — a long-running service' : 'No — a one-off task' },
    { label: 'Telemetry', icon: markRaw(Radio), value: w?.telemetry ? 'Traces and metrics shipped through the node' : 'Off' },
    { label: 'Log policy', icon: markRaw(FileText),
      value: w?.logPolicy ? `${w.logPolicy.maxSizeMb} MB × ${w.logPolicy.maxFiles} files` : '—' },
    { label: 'Created', icon: markRaw(CalendarPlus), value: w?.created ? formatEpochDateTime(w.created) : null },
    { label: 'Updated', icon: markRaw(CalendarClock), value: w?.updated ? formatEpochDateTime(w.updated) : null }
  ]
})

interface Stat {
  label: string
  value: string
  detail: string
  /** Renders the value as a Tag of this severity instead of a number. */
  tag?: string
  to?: string
  icon?: Component
  /** Shows a HeartbeatIcon in this state in place of the icon. */
  heartbeat?: HeartbeatState
  /** One of TINTS. */
  tint: string
}

/** The heartbeat a workload's status shows: alive while running, failed once failed, idle otherwise. */
function workloadHeartbeat(status: WorkloadStatus): HeartbeatState {
  let ret: HeartbeatState
  if (status === WorkloadStatus.RUNNING) {
    ret = HeartbeatState.ALIVE
  } else if (status === WorkloadStatus.FAILED) {
    ret = HeartbeatState.FAILED
  } else {
    ret = HeartbeatState.IDLE
  }
  return ret
}

const stats = computed<Stat[]>(() => {
  const w = workload.value
  if (!w) return []
  let ownerName: string
  let ownerDetail: string
  let ownerTo: string
  let ownerIcon: Component
  if (w.organizationId && w.applicationId) {
    ownerName = w.applicationId
    ownerDetail = `application of ${w.organizationId}`
    ownerTo = applicationPath(w.organizationId, w.applicationId)
    ownerIcon = LayoutGrid
  } else if (w.organizationId) {
    ownerName = w.organizationId
    ownerDetail = 'the organization itself'
    ownerTo = organizationPath(w.organizationId)
    ownerIcon = Building2
  } else {
    ownerName = 'platform'
    ownerDetail = 'runs for the platform itself'
    ownerTo = '/cluster'
    ownerIcon = Shield
  }
  return [
    {
      label: 'Status',
      value: w.status,
      detail: w.exitCode !== null ? `exit code ${w.exitCode}` : `since ${formatEpochDateTime(w.updated ?? w.created)}`,
      tag: workloadSeverity(w.status),
      heartbeat: workloadHeartbeat(w.status),
      tint: HEARTBEAT_TINTS[workloadHeartbeat(w.status)]
    },
    {
      label: 'Node',
      value: node.value?.name ?? w.nodeId ?? '—',
      detail: node.value ? `${nodeHealth(node.value).toLowerCase()} · ${node.value.providerType}` : 'not placed yet',
      to: w.nodeId ? `/worker-nodes/${encodeURIComponent(w.nodeId)}` : undefined,
      icon: markRaw(Server),
      tint: tint.value
    },
    {
      label: 'Owner',
      value: ownerName,
      detail: ownerDetail,
      to: ownerTo,
      icon: markRaw(ownerIcon),
      tint: tint.value
    },
    {
      label: 'Resources',
      value: `${formatCpus(w.cpus)} CPU`,
      detail: `${formatMb(w.memoryMb)} memory · ${formatMb(w.diskSizeMb)} disk`,
      icon: markRaw(Cpu),
      tint: tint.value
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    workload.value = await Kinotic.workloads.findById(props.workloadId)
    const [placedOn, entries] = await Promise.all([
      workload.value.nodeId ? Kinotic.vmNodes.findById(workload.value.nodeId).catch(() => null) : Promise.resolve(null),
      Kinotic.workloads.findHistory(props.workloadId, Pageable.create(0, HISTORY_PAGE_SIZE))
    ])
    node.value = placedOn
    history.value = entries.content ?? []
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the workload')
  } finally {
    loading.value = false
  }
}

async function act(action: () => Promise<unknown>, successMessage: string, failureMessage: string) {
  try {
    await action()
    toast.add({ severity: 'success', summary: successMessage, life: 4000 })
    await load()
  } catch (err) {
    showErrorToast(toast, failureMessage, err, { life: 8000 })
  }
}

function confirmDestroy() {
  confirm.require({
    header: 'Confirm destroy',
    message: `Destroy workload ${workload.value?.name}? Its VM and disk are removed permanently; its record and logs stay.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Destroy', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => act(() => Kinotic.workloadOrchestration.destroyWorkload(props.workloadId), 'Workload destroyed', 'Failed to destroy workload')
  })
}

function confirmDelete() {
  confirm.require({
    header: 'Confirm delete',
    message: `Delete workload ${workload.value?.name}? Its record and its logs are removed permanently.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.workloadOrchestration.deleteWorkload(props.workloadId)
        toast.add({ severity: 'success', summary: 'Workload deleted', life: 4000 })
        emit('deleted', props.workloadId)
        if (!embedded) {
          router.push(listPath.value)
        }
      } catch (err) {
        showErrorToast(toast, 'Failed to delete workload', err, { life: 8000 })
      }
    }
  })
}

watch(() => props.workloadId, load, { immediate: true })
</script>
