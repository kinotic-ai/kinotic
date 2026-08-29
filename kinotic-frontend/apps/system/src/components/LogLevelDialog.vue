<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Log level — ${nodeId}`"
    :style="{ width: '28rem', maxWidth: '95vw' }"
    @show="opened"
  >
    <p class="mb-4 text-sm text-muted-color">
      Changes the level of a logger on this node immediately, without a restart. The change
      lasts until the node restarts.
    </p>

    <div class="flex flex-col gap-3">
      <InputText
        v-model="loggerName"
        placeholder="Logger name (e.g. org.kinotic)"
        autofocus
        @keyup.enter="apply"
      />
      <Select v-model="level" :options="levels" placeholder="Level" />
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" outlined @click="visible = false" />
      <Button label="Apply" :disabled="!canApply" :loading="applying" @click="apply" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { LogLevel } from '@kinotic-ai/system-api'
import { showErrorToast } from '@kinotic-ai/frontend-common'

const props = defineProps<{
  nodeId: string
}>()

const visible = defineModel<boolean>('visible', { required: true })

const toast = useToast()

const loggerName = ref('')
const level = ref<LogLevel | null>(null)
const applying = ref(false)

const levels = Object.values(LogLevel)

const canApply = computed(() => loggerName.value.trim() !== '' && level.value !== null)

function opened() {
  loggerName.value = ''
  level.value = null
}

async function apply() {
  if (!canApply.value) {
    return
  }
  applying.value = true
  try {
    await Kinotic.logManager.configureLogLevel(props.nodeId, loggerName.value.trim(), level.value!)
    toast.add({
      severity: 'success',
      summary: 'Log level set',
      detail: `${loggerName.value.trim()} → ${level.value} on ${props.nodeId}`,
      life: 4000
    })
    visible.value = false
  } catch (err) {
    showErrorToast(toast, 'Failed to set log level', err)
  } finally {
    applying.value = false
  }
}
</script>
