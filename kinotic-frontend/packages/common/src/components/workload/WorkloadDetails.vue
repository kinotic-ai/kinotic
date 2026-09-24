<template>
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
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { NetworkMode, type Workload } from '@kinotic-ai/management-api'

import DatetimeUtil from '../../util/DatetimeUtil'

/**
 * Everything a workload's record holds short of secret values, in four cards: how it runs,
 * what it may reach and publishes, the names of its environment variables, and its mounts.
 */
const props = defineProps<{
  workload: Workload
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const command = computed(() => [...(props.workload.entrypoint ?? []), ...(props.workload.cmd ?? [])].join(' '))
const allowedHosts = computed(() => props.workload.network?.allowedHosts ?? [])
const ports = computed(() => (props.workload.portMappings ?? []).map(port =>
    `${port.hostIp ? `${port.hostIp}:` : ''}${port.hostPort ?? port.guestPort}→${port.guestPort}/${(port.protocol ?? 'TCP').toLowerCase()}`))
const environmentNames = computed(() => Object.keys(props.workload.environment ?? {}).sort())
const volumes = computed(() => (props.workload.volumeMounts ?? []).map(volume =>
    `${volume.hostPath} → ${volume.guestPath}${volume.readOnly ? ' (ro)' : ''}`))
</script>
