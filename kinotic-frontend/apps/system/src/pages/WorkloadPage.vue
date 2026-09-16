<template>
  <div class="flex flex-col">
    <PageHeader :title="workload?.name ?? workloadId">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Workloads</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate">{{ workload?.name ?? workloadId }}</span>
      </template>
      <template #actions>
        <Tag v-if="workload" :value="workload.status" :severity="workloadSeverity(workload.status)" />
        <Button label="View logs" icon="pi pi-align-left" severity="secondary" outlined @click="tab = 'logs'" />
        <Button v-if="canStop" label="Stop" icon="pi pi-stop-circle" severity="secondary" outlined
                @click="act(() => Kinotic.workloadOrchestration.stopWorkload(workloadId), 'Workload stopping', 'Failed to stop workload')" />
        <Button v-if="canRestart" label="Restart" icon="pi pi-replay" severity="secondary" outlined
                @click="act(() => Kinotic.workloadOrchestration.restartWorkload(workloadId), 'Workload restarting', 'Failed to restart workload')" />
        <Button label="Destroy" icon="pi pi-trash" severity="danger" outlined :disabled="!workload" @click="confirmDestroy" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <Tabs v-model:value="tab">
      <TabList>
        <Tab value="overview"><i class="pi pi-objects-column mr-2" />Overview</Tab>
        <Tab value="logs"><i class="pi pi-align-left mr-2" />Logs</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="overview">
          <div v-if="workload" class="flex flex-col gap-4 pt-2">
            <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
              <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
            </div>

            <Message v-if="workload.status === WorkloadStatus.FAILED" severity="error" :closable="false">
              The VM exited{{ workload.exitCode !== null ? ` with code ${workload.exitCode}` : '' }}. Its last log lines are on the Logs tab.
            </Message>

            <div class="grid gap-4 lg:grid-cols-2">
              <div class="rounded-lg border border-surface p-4">
                <h2 class="mb-2 text-base font-semibold">Runtime</h2>
                <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
                  <dt class="text-muted-color">Image</dt>
                  <dd class="break-all font-mono">{{ workload.image }}</dd>
                  <dt class="text-muted-color">Command</dt>
                  <dd class="break-all font-mono">{{ command || '—' }}</dd>
                  <dt class="text-muted-color">Detached</dt>
                  <dd>{{ workload.detached ? 'Yes — a long-running service' : 'No — a one-off task' }}</dd>
                  <dt class="text-muted-color">Auto remove</dt>
                  <dd>{{ workload.autoRemove ? 'Yes — the VM is removed once it exits' : 'No' }}</dd>
                  <dt class="text-muted-color">Telemetry</dt>
                  <dd>{{ workload.telemetry ? 'Traces and metrics shipped through the node' : 'Off' }}</dd>
                  <dt class="text-muted-color">Log policy</dt>
                  <dd>{{ workload.logPolicy ? `${workload.logPolicy.maxSizeMb} MB × ${workload.logPolicy.maxFiles} files` : '—' }}</dd>
                  <dt class="text-muted-color">Created</dt>
                  <dd>{{ formatEpochDateTime(workload.created) }}</dd>
                  <dt class="text-muted-color">Updated</dt>
                  <dd>{{ formatEpochDateTime(workload.updated) }}</dd>
                </dl>
              </div>

              <div class="rounded-lg border border-surface p-4">
                <h2 class="mb-2 text-base font-semibold">Network</h2>
                <div class="mb-1 text-xs font-medium uppercase tracking-wide text-muted-color">Allowed hosts</div>
                <div v-if="workload.network?.mode === NetworkMode.DISABLED" class="text-sm text-muted-color">Networking is disabled for this VM.</div>
                <div v-else-if="allowedHosts.length === 0" class="text-sm text-muted-color">No host is allowed; the node adds the resolver and, for telemetry, its own OTLP endpoint.</div>
                <div v-else class="flex flex-wrap gap-1.5">
                  <span v-for="host in allowedHosts" :key="host" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ host }}</span>
                </div>
                <p class="mt-2 mb-4 text-xs text-muted-color">Every other destination is blocked.</p>
                <div class="mb-1 text-xs font-medium uppercase tracking-wide text-muted-color">Ports</div>
                <div v-if="ports.length === 0" class="text-sm text-muted-color">None published</div>
                <div v-else class="flex flex-wrap gap-1.5">
                  <span v-for="port in ports" :key="port" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ port }}</span>
                </div>
              </div>

              <div class="rounded-lg border border-surface p-4">
                <h2 class="mb-2 text-base font-semibold">Environment</h2>
                <div v-if="environmentNames.length === 0" class="text-sm text-muted-color">No environment variables.</div>
                <div v-else class="flex flex-wrap gap-1.5">
                  <span v-for="name in environmentNames" :key="name" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ name }}</span>
                </div>
                <p class="mt-2 text-xs text-muted-color">Names only. Values and secrets are not shown.</p>
              </div>

              <div class="rounded-lg border border-surface p-4">
                <h2 class="mb-2 text-base font-semibold">Volumes</h2>
                <div v-if="volumes.length === 0" class="text-sm text-muted-color">No volume mounts; the VM has its own disk only.</div>
                <div v-else class="flex flex-wrap gap-1.5">
                  <span v-for="volume in volumes" :key="volume" class="rounded-md bg-emphasis px-2 py-0.5 font-mono text-xs">{{ volume }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading workload…</div>
        </TabPanel>
        <TabPanel value="logs">
          <!-- Mounted with the tab, so a return starts a fresh history load and tail -->
          <WorkloadLogView v-if="tab === 'logs'" :workload-id="workloadId" :workload="workload ?? undefined" class="pt-2" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

import { Kinotic } from '@kinotic-ai/core'
import { NetworkMode, WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, PageHeader, WorkloadLogView, errorMessage, formatMb, showErrorToast } from '@kinotic-ai/frontend-common'

import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
import { applicationPath, organizationPath, scopePath, type Scope } from '@/util/scope'
import { workloadSeverity } from '@/util/workloads'

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

const workload = ref<Workload | null>(null)
const node = ref<VmNode | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// The tab lives in the URL so a row menu can open the logs directly
const tab = computed<string>({
  get: () => route.query.tab === 'logs' ? 'logs' : 'overview',
  set: value => { router.replace({ query: { ...route.query, tab: value === 'logs' ? 'logs' : undefined } }) }
})

const canStop = computed(() => workload.value?.status === WorkloadStatus.RUNNING || workload.value?.status === WorkloadStatus.STARTING)
// A workload stopped with autoRemove has no VM left to restart
const canRestart = computed(() => (workload.value?.status === WorkloadStatus.STOPPED && !workload.value.autoRemove)
    || workload.value?.status === WorkloadStatus.FAILED)

const command = computed(() => [...(workload.value?.entrypoint ?? []), ...(workload.value?.cmd ?? [])].join(' '))
const allowedHosts = computed(() => workload.value?.network?.allowedHosts ?? [])
const ports = computed(() => (workload.value?.portMappings ?? []).map(port =>
    `${port.hostIp ? `${port.hostIp}:` : ''}${port.hostPort ?? port.guestPort}→${port.guestPort}/${(port.protocol ?? 'TCP').toLowerCase()}`))
const environmentNames = computed(() => Object.keys(workload.value?.environment ?? {}).sort())
const volumes = computed(() => (workload.value?.volumeMounts ?? []).map(volume =>
    `${volume.hostPath} → ${volume.guestPath}${volume.readOnly ? ' (ro)' : ''}`))

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
  const w = workload.value
  if (!w) return []
  let ownerName: string
  let ownerDescription: string
  let ownerTo: string
  let ownerIcon: string
  if (w.organizationId && w.applicationId) {
    ownerName = w.applicationId
    ownerDescription = `application of ${w.organizationId}`
    ownerTo = applicationPath(w.organizationId, w.applicationId)
    ownerIcon = 'pi-th-large'
  } else if (w.organizationId) {
    ownerName = w.organizationId
    ownerDescription = 'the organization itself'
    ownerTo = organizationPath(w.organizationId)
    ownerIcon = 'pi-building'
  } else {
    ownerName = 'platform'
    ownerDescription = 'runs for the platform itself'
    ownerTo = '/cluster'
    ownerIcon = 'pi-shield'
  }
  return [
    {
      label: 'Status',
      value: w.status,
      description: w.exitCode !== null ? `exit code ${w.exitCode}` : `since ${formatEpochDateTime(w.updated ?? w.created)}`,
      tag: workloadSeverity(w.status),
      icon: 'pi-wave-pulse',
      accent: w.status === WorkloadStatus.FAILED ? 'red' : 'green'
    },
    {
      label: 'Node',
      value: node.value?.name ?? w.nodeId ?? '—',
      description: node.value ? `${node.value.status.type.toLowerCase()} · ${node.value.providerType}` : 'not placed yet',
      to: w.nodeId ? `/worker-nodes/${encodeURIComponent(w.nodeId)}` : undefined,
      icon: 'pi-server',
      accent: 'amber'
    },
    {
      label: 'Owner',
      value: ownerName,
      description: ownerDescription,
      to: ownerTo,
      icon: ownerIcon,
      accent: 'teal'
    },
    {
      label: 'Resources',
      value: `${w.vcpus} vCPU`,
      description: `${formatMb(w.memoryMb)} memory · ${formatMb(w.diskSizeMb)} disk`,
      icon: 'pi-microchip',
      accent: 'sky'
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    workload.value = await Kinotic.workloads.findById(props.workloadId)
    node.value = workload.value.nodeId ? await Kinotic.vmNodes.findById(workload.value.nodeId).catch(() => null) : null
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
    message: `Destroy workload ${workload.value?.name}? Its VM and disk are removed permanently.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Destroy', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.workloadOrchestration.destroyWorkload(props.workloadId)
        toast.add({ severity: 'success', summary: 'Workload destroyed', life: 4000 })
        router.push(listPath.value)
      } catch (err) {
        showErrorToast(toast, 'Failed to destroy workload', err, { life: 8000 })
      }
    }
  })
}

watch(() => props.workloadId, load, { immediate: true })
</script>
