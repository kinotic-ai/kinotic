<template>
  <FormDrawer :visible="visible" title="Run system job"
              description="A system job, run now on behalf of the system." @close="emit('update:visible', false)">
    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>
    <p v-else-if="!loading && jobs.length === 0" class="text-sm text-muted-color">No system jobs are available.</p>

    <form id="run-system-job-form" class="flex flex-col gap-3" @submit.prevent="start">
      <label v-for="job in jobs" :key="job.name" :for="`system-job-${job.name}`"
             :class="['flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors',
                      selected === job.name
                        ? 'border-blue-500 bg-blue-500/10'
                        : 'border-surface-200 hover:bg-surface-50 dark:border-surface-700 dark:hover:bg-surface-800/40']">
        <RadioButton v-model="selected" :input-id="`system-job-${job.name}`" name="system-job" :value="job.name" :dt="BLUE_RADIO" class="mt-0.5" />
        <span class="min-w-0 flex-1">
          <span class="flex items-center gap-2">
            <span class="min-w-0 truncate font-mono text-sm font-semibold text-surface-950 dark:text-surface-0">{{ job.name }}</span>
            <span v-if="job.version" class="shrink-0 rounded-md bg-surface-100 px-1.5 py-0.5 text-xs text-surface-600 dark:bg-surface-800 dark:text-surface-300">v{{ job.version }}</span>
          </span>
          <span v-if="job.description" class="mt-1 block text-sm text-muted-color">{{ job.description }}</span>
        </span>
      </label>
    </form>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" form="run-system-job-form" label="Run" icon="pi pi-play" :loading="starting" :disabled="!selected" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import RadioButton from 'primevue/radiobutton'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import type { SystemJobDescriptor } from '@kinotic-ai/system-api'
import { FormDrawer, showErrorToast } from '@kinotic-ai/frontend-common'

/**
 * Picks one of the system jobs and starts a run of it. Lists the jobs each time it
 * opens, and emits {@code started} with the run's id once the run is recorded.
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'started', jobRunId: string): void
}>()

// Blue marks the selected job, leaving the brand red to failures
const BLUE_RADIO = {
  root: {
    checkedBackground: '{blue.500}',
    checkedHoverBackground: '{blue.600}',
    checkedBorderColor: '{blue.500}',
    checkedHoverBorderColor: '{blue.600}',
    checkedFocusBorderColor: '{blue.500}'
  }
}

const toast = useToast()

const jobs = ref<SystemJobDescriptor[]>([])
const selected = ref<string | null>(null)
const loading = ref(false)
const starting = ref(false)
const error = ref<string | null>(null)

watch(() => props.visible, visible => {
  if (visible) {
    // the last opening's list, choice and error go at once, so nothing stale can be picked while the jobs reload
    jobs.value = []
    selected.value = null
    error.value = null
    void loadJobs()
  }
})

async function loadJobs(): Promise<void> {
  loading.value = true
  try {
    jobs.value = await Kinotic.systemJobs.findSystemJobs()
    // a single job is the only choice there is
    selected.value = jobs.value.length === 1 ? jobs.value[0].name : null
  } catch (err) {
    error.value = `Could not list the system jobs: ${err instanceof Error ? err.message : String(err)}`
  } finally {
    loading.value = false
  }
}

async function start(): Promise<void> {
  if (!selected.value) {
    return
  }
  starting.value = true
  try {
    const run = await Kinotic.systemJobs.startSystemJob(selected.value)
    emit('update:visible', false)
    if (run.id) {
      emit('started', run.id)
    }
  } catch (err) {
    showErrorToast(toast, `Failed to run ${selected.value}`, err)
  } finally {
    starting.value = false
  }
}
</script>
