<template>
  <DataTable :value="deployments" size="small">
    <Column header="UI" style="width: 20%">
      <template #body="{ data }"><span class="font-mono text-sm">{{ data.name }}</span></template>
    </Column>
    <Column header="Status" style="width: 14%">
      <template #body="{ data }">
        <span :title="data.observation ?? undefined">
          <Tag :value="observedPhase(data.state.observed)" :severity="observedPhaseSeverity(data.state.observed)" />
        </span>
        <Tag v-if="data.state.deletionRequested" value="removing" severity="secondary" class="ml-1" />
      </template>
    </Column>
    <Column header="Site" style="width: 34%">
      <template #body="{ data }">
        <a :href="data.url" target="_blank" rel="noopener" class="font-mono text-sm break-all">{{ data.url }}</a>
      </template>
    </Column>
    <Column header="Commit" style="width: 12%">
      <template #body="{ data }">
        <span class="font-mono text-sm text-muted-color" :title="data.state.observed?.commitSha ?? undefined">
          {{ data.state.observed?.commitSha ? shortSha(data.state.observed.commitSha) : '—' }}
        </span>
      </template>
    </Column>
    <Column style="width: 20%">
      <template #body="{ data }">
        <div class="flex justify-end gap-1">
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
import { observedPhase, observedPhaseSeverity, shortSha } from '@kinotic-ai/frontend-common'
import type { UiDeployment } from '@kinotic-ai/management-api'

/**
 * The UIs a project's deployments have published, one row each with its site, the phase it
 * reports, the commit the site serves, and the one action the console offers: removal.
 */
defineProps<{
  deployments: UiDeployment[]
}>()

const emit = defineEmits<{
  remove: [deployment: UiDeployment]
}>()
</script>
