<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Logs — ${telemetryServiceInstanceId ?? telemetryServiceName}`"
    :style="{ width: '95vw' }"
  >
    <p v-if="!telemetryServiceInstanceId" class="mb-3 text-xs text-muted-color">
      This node reports no service instance id, so every node of {{ telemetryServiceName }} is shown, interleaved.
    </p>
    <!-- Mounted with the dialog, so a reopen starts a fresh history load and tail -->
    <LogView v-if="visible" :source="source" />
  </Dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Dialog from 'primevue/dialog'
import { Kinotic } from '@kinotic-ai/core'
import { LogView, type LogSource } from '@kinotic-ai/frontend-common'

/** A server node's logs in a dialog, following them live. */
const props = defineProps<{
  /** The service name that labels the server's logs, as its cluster nodes report it. */
  telemetryServiceName: string
  /** The service instance id that labels the node's own logs; null shows every node of the server. */
  telemetryServiceInstanceId: string | null
}>()

const visible = defineModel<boolean>('visible', { required: true })

const source = computed<LogSource>(() => ({
  history: (start, end, limit) => Kinotic.logs.serverHistory({
    telemetryServiceName: props.telemetryServiceName,
    telemetryServiceInstanceId: props.telemetryServiceInstanceId,
    start,
    end,
    limit
  }),
  tail: () => Kinotic.logs.tailServer(props.telemetryServiceName, props.telemetryServiceInstanceId)
}))
</script>
