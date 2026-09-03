<template>
  <DataTable :value="deployments" size="small">
    <Column header="UI" style="width: 16%">
      <template #body="{ data }"><span class="font-mono text-sm">{{ data.name }}</span></template>
    </Column>
    <Column header="Site" style="width: 34%">
      <template #body="{ data }">
        <a :href="data.url" target="_blank" rel="noopener" class="font-mono text-sm break-all">{{ data.url }}</a>
      </template>
    </Column>
    <Column header="Status" style="width: 14%">
      <template #body="{ data }">
        <span :title="data.status.message ?? undefined">
          <Tag :value="data.status.type" :severity="deploymentStatusSeverity(data.status.type)" />
        </span>
      </template>
    </Column>
    <Column header="Commit" style="width: 12%">
      <template #body="{ data }">
        <span class="font-mono text-sm text-muted-color" :title="data.commitSha ?? undefined">
          {{ data.commitSha ? shortSha(data.commitSha) : '—' }}
        </span>
      </template>
    </Column>
    <Column style="width: 24%">
      <template #body="{ data }">
        <div class="flex justify-end gap-1">
          <Button label="Retry" icon="pi pi-refresh" size="small" severity="secondary" text
                  :disabled="data.status.type !== UiDeploymentStatusType.FAILED" @click="emit('retry', data)" />
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
import { deploymentStatusSeverity, shortSha } from '@kinotic-ai/frontend-common'
import { UiDeploymentStatusType, type UiDeployment } from '@kinotic-ai/management-api'

/**
 * The UIs a project's deployments have published, one row each with its site, its status,
 * the commit the site serves, and the actions the console offers: provisioning a failed site
 * again, and removal.
 */
defineProps<{
  deployments: UiDeployment[]
}>()

const emit = defineEmits<{
  retry: [deployment: UiDeployment]
  remove: [deployment: UiDeployment]
}>()
</script>
