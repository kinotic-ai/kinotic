<template>
  <DataTable :value="deployments" size="small">
    <Column header="Microservice" style="width: 18%">
      <template #body="{ data }"><span class="font-mono text-sm">{{ data.name }}</span></template>
    </Column>
    <Column header="Status" style="width: 14%">
      <template #body="{ data }">
        <span :title="data.status.message ?? undefined">
          <Tag :value="data.status.type" :severity="statusSeverity(data.status.type)" />
        </span>
      </template>
    </Column>
    <Column header="Commit" style="width: 12%">
      <template #body="{ data }">
        <span class="font-mono text-sm text-muted-color" :title="data.commitSha ?? undefined">
          {{ data.commitSha ? data.commitSha.slice(0, 12) : '—' }}
        </span>
      </template>
    </Column>
    <Column header="Entry" style="width: 30%">
      <template #body="{ data }"><span class="font-mono text-xs">{{ data.entry ?? '—' }}</span></template>
    </Column>
    <Column style="width: 26%">
      <template #body="{ data }">
        <div class="flex justify-end gap-1">
          <Button label="Logs" icon="pi pi-align-left" size="small" severity="secondary" text
                  :disabled="!data.workloadId" @click="emit('logs', data)" />
          <Button label="Restart" icon="pi pi-refresh" size="small" severity="secondary" text
                  :disabled="!data.workloadId" @click="emit('restart', data)" />
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
import { MicroserviceDeploymentStatusType, type MicroserviceDeployment } from '@kinotic-ai/management-api'

/**
 * The microservices a project's deployments have ensured, one row each with its status, the
 * commit it was ensured for, the module it runs, and the actions the console offers: its
 * workload's log, a restart of its VM, and removal.
 */
defineProps<{
  deployments: MicroserviceDeployment[]
}>()

const emit = defineEmits<{
  logs: [deployment: MicroserviceDeployment]
  restart: [deployment: MicroserviceDeployment]
  remove: [deployment: MicroserviceDeployment]
}>()

function statusSeverity(type: MicroserviceDeploymentStatusType): string {
  let ret: string
  if (type === MicroserviceDeploymentStatusType.DEPLOYED) {
    ret = 'success'
  } else if (type === MicroserviceDeploymentStatusType.FAILED) {
    ret = 'danger'
  } else {
    ret = 'warn'
  }
  return ret
}
</script>
