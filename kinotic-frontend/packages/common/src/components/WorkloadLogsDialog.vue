<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Logs — ${workloadName}`"
    :style="{ width: '70rem', maxWidth: '95vw' }"
  >
    <!-- Mounted with the dialog, so a reopen starts a fresh history load and tail -->
    <WorkloadLogView v-if="visible" :workload-id="workloadId" :workload="workload" />
  </Dialog>
</template>

<script setup lang="ts">
import Dialog from 'primevue/dialog'
import type { Workload } from '@kinotic-ai/management-api'
import WorkloadLogView from './WorkloadLogView.vue'

/** The log view in a dialog; given the workload's record, the view opens on its run once it has ended. */
defineProps<{
  workloadId: string
  workloadName: string
  workload?: Workload
}>()

const visible = defineModel<boolean>('visible', { required: true })
</script>
