<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Logs — ${workloadName}`"
    :style="{ width: '70rem', maxWidth: '95vw' }"
  >
    <!-- Mounted with the dialog, so a reopen starts a fresh history load and tail -->
    <WorkloadLogView v-if="visible" :workload-id="workloadId" />
  </Dialog>
</template>

<script setup lang="ts">
import Dialog from 'primevue/dialog'
import { WorkloadLogView } from '@kinotic-ai/frontend-common'

defineProps<{
  workloadId: string
  workloadName: string
}>()

const visible = defineModel<boolean>('visible', { required: true })
</script>
