<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Logging — ${nodeId}`"
    :style="{ width: '52rem', maxWidth: '95vw' }"
    @show="opened"
  >
    <p class="mb-4 text-sm text-muted-color">
      Changes logging on this node immediately, without a restart. Changes last until the node
      restarts.
    </p>

    <h3 class="mb-2 text-sm font-semibold">Log level</h3>
    <div class="flex flex-col gap-3">
      <InputText
        v-model="loggerName"
        placeholder="Logger name (e.g. org.kinotic)"
        autofocus
        @keyup.enter="applyLevel"
      />
      <Select v-model="level" :options="levels" placeholder="Level" />
      <div>
        <Button label="Set level" size="small" :disabled="!canApplyLevel" :loading="applyingLevel"
                @click="applyLevel" />
      </div>
    </div>

    <Divider />

    <h3 class="mb-2 text-sm font-semibold">Trace log filters</h3>
    <p class="mb-3 text-xs text-muted-color">
      CRI patterns deciding what TRACE prints. One pattern per line, matched against the whole CRI — <code>*</code> within a segment,
      <code>**</code> across segments. An include wins over an exclude, so excluding
      <code>**</code> and listing a few includes narrows TRACE to those services alone.
    </p>

    <Message v-if="traceLogError" severity="error" :closable="false" class="mb-3">{{ traceLogError }}</Message>

    <div class="flex flex-col gap-3">
      <div>
        <label class="mb-1 block text-xs text-muted-color" for="trace-log-excludes">Excludes</label>
        <Textarea
          id="trace-log-excludes"
          v-model="excludes"
          class="w-full font-mono text-xs"
          rows="3"
          :disabled="loadingTraceLog"
          placeholder="srv://system-api~org.kinotic.system.api.services.VmNodeOrchestrationService/*"
        />
      </div>
      <div>
        <label class="mb-1 block text-xs text-muted-color" for="trace-log-includes">Includes</label>
        <Textarea
          id="trace-log-includes"
          v-model="includes"
          class="w-full font-mono text-xs"
          rows="3"
          :disabled="loadingTraceLog"
          placeholder="srv://app.acme-org.orders-app~com.acme.OrderService/**"
        />
      </div>
      <div>
        <Button label="Save filters" size="small" :disabled="loadingTraceLog" :loading="savingTraceLog"
                @click="saveTraceLog" />
      </div>
    </div>

    <template #footer>
      <Button label="Close" severity="secondary" outlined @click="visible = false" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Divider from 'primevue/divider'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { LogLevel, TraceLogProperties } from '@kinotic-ai/system-api'
import { showErrorToast } from '@kinotic-ai/frontend-common'

const props = defineProps<{
  nodeId: string
}>()

const visible = defineModel<boolean>('visible', { required: true })

const toast = useToast()

const loggerName = ref('')
const level = ref<LogLevel | null>(null)
const applyingLevel = ref(false)

const includes = ref('')
const excludes = ref('')
const loadingTraceLog = ref(false)
const savingTraceLog = ref(false)
const traceLogError = ref<string | null>(null)

const levels = Object.values(LogLevel)

const canApplyLevel = computed(() => loggerName.value.trim() !== '' && level.value !== null)

function opened() {
  loggerName.value = ''
  level.value = null
  loadTraceLog()
}

function toPatterns(text: string): string[] {
  return text.split('\n').map(line => line.trim()).filter(line => line !== '')
}

async function loadTraceLog() {
  loadingTraceLog.value = true
  traceLogError.value = null
  try {
    const traceLog = await Kinotic.logManager.traceLog(props.nodeId)
    includes.value = traceLog.includes.join('\n')
    excludes.value = traceLog.excludes.join('\n')
  } catch (err) {
    traceLogError.value = `Could not read the trace log filters: ${err}`
  } finally {
    loadingTraceLog.value = false
  }
}

async function applyLevel() {
  if (!canApplyLevel.value) {
    return
  }
  applyingLevel.value = true
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
    applyingLevel.value = false
  }
}

async function saveTraceLog() {
  savingTraceLog.value = true
  try {
    const traceLog = new TraceLogProperties()
    traceLog.includes = toPatterns(includes.value)
    traceLog.excludes = toPatterns(excludes.value)
    await Kinotic.logManager.configureTraceLog(props.nodeId, traceLog)
    toast.add({
      severity: 'success',
      summary: 'Trace log filters saved',
      detail: `${traceLog.includes.length} include(s), ${traceLog.excludes.length} exclude(s) on ${props.nodeId}`,
      life: 4000
    })
  } catch (err) {
    showErrorToast(toast, 'Failed to save trace log filters', err)
  } finally {
    savingTraceLog.value = false
  }
}
</script>
