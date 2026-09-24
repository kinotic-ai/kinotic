<template>
  <DataTable :value="deployments" size="small">
    <Column header="Microservice" style="width: 20%">
      <template #body="{ data }"><span class="font-mono text-sm">{{ data.name }}</span></template>
    </Column>
    <Column header="Status" style="width: 14%">
      <template #body="{ data }">
        <span :title="data.failureMessage ?? undefined">
          <Tag :value="phaseOf(data)" :severity="phaseSeverity(data)" />
        </span>
        <Tag v-if="unreachable(data)" value="node unreachable" severity="warn" icon="pi pi-exclamation-triangle"
             class="ml-1" :title="unreachable(data)?.message" />
        <Tag v-if="data.state.deletionRequested" value="removing" severity="secondary" class="ml-1" />
      </template>
    </Column>
    <Column header="Commit" style="width: 12%">
      <template #body="{ data }">
        <span class="font-mono text-sm text-muted-color" :title="data.state.observed?.commitSha ?? undefined">
          {{ data.state.observed?.commitSha ? shortSha(data.state.observed.commitSha) : '—' }}
        </span>
      </template>
    </Column>
    <Column header="Entry point" style="width: 28%">
      <template #body="{ data }"><span class="font-mono text-xs">{{ data.entryPoint ?? '—' }}</span></template>
    </Column>
    <Column style="width: 26%">
      <template #body="{ data }">
        <div class="flex justify-end gap-1">
          <Button label="Logs" icon="pi pi-align-left" size="small" severity="secondary" text
                  :disabled="!data.workloadId" @click="emit('logs', data)" />
          <Button label="Restart" icon="pi pi-refresh" size="small" severity="secondary" text
                  :disabled="!data.entryPoint" @click="emit('restart', data)" />
          <Button label="Remove" icon="pi pi-trash" size="small" severity="danger" text
                  @click="emit('remove', data)" />
        </div>
      </template>
    </Column>
  </DataTable>
</template>

<script setup lang="ts">
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Tag from 'primevue/tag'
import { StatusConditionType, findStatusCondition, type StatusCondition } from '@kinotic-ai/core'
import { deploymentStatusSeverity, shortSha } from '@kinotic-ai/frontend-common'
import type { MicroserviceDeployment } from '@kinotic-ai/management-api'

/**
 * The microservices a project's deployments have ensured, one row each with the phase it reports,
 * the commit it serves, the module it runs, and the actions the console offers: its workload's
 * log, a restart of its VM, and removal.
 */
defineProps<{
  deployments: MicroserviceDeployment[]
}>()

/** The phase the deployment reports, or what it has yet to report. */
function phaseOf(deployment: MicroserviceDeployment): string {
  return deployment.state.observed?.phase ?? 'PENDING'
}

function phaseSeverity(deployment: MicroserviceDeployment): string {
  const phase = deployment.state.observed?.phase
  return phase ? deploymentStatusSeverity(phase) : 'secondary'
}

/** The mark that the node running the microservice has not answered, or undefined while it does. */
function unreachable(deployment: MicroserviceDeployment): StatusCondition | undefined {
  return findStatusCondition(deployment.state.conditions, StatusConditionType.NODE_UNREACHABLE)
}

const emit = defineEmits<{
  logs: [deployment: MicroserviceDeployment]
  restart: [deployment: MicroserviceDeployment]
  remove: [deployment: MicroserviceDeployment]
}>()
</script>
