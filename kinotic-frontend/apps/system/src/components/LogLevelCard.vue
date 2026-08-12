<template>
  <Card>
    <template #title>Log level</template>
    <template #content>
      <div class="flex flex-wrap items-center gap-3">
        <Select
          v-model="nodeId"
          :options="nodeIds"
          placeholder="Node"
          class="min-w-48"
        />
        <InputText
          v-model="loggerName"
          placeholder="Logger name (e.g. org.kinotic)"
          class="min-w-48"
          @keyup.enter="apply"
        />
        <Select
          v-model="level"
          :options="levels"
          placeholder="Level"
          class="min-w-48"
        />
        <Button label="Apply" :disabled="!canApply" :loading="applying" @click="apply" />
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { LogLevel } from '@kinotic-ai/os-api'
import { showErrorToast } from '@kinotic-ai/frontend-common'

defineProps<{
  nodeIds: string[]
}>()

const toast = useToast()

const nodeId = ref<string | null>(null)
const loggerName = ref('')
const level = ref<LogLevel | null>(null)
const applying = ref(false)

const levels = Object.values(LogLevel)

const canApply = computed(() => nodeId.value !== null && loggerName.value.trim() !== '' && level.value !== null)

async function apply() {
  if (!canApply.value) {
    return
  }
  applying.value = true
  try {
    await Kinotic.logManager.configureLogLevel(nodeId.value!, loggerName.value.trim(), level.value!)
    toast.add({
      severity: 'success',
      summary: 'Log level set',
      detail: `${loggerName.value.trim()} → ${level.value} on ${nodeId.value}`,
      life: 4000
    })
  } catch (err) {
    showErrorToast(toast, 'Failed to set log level', err)
  } finally {
    applying.value = false
  }
}
</script>
