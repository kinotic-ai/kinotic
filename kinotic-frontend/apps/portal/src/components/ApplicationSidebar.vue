<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { Check, Circle, LoaderCircle } from '@lucide/vue'
import { createDebug } from '@kinotic-ai/frontend-common'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import type {Application} from "@kinotic-ai/management-api";
import {Kinotic} from "@kinotic-ai/core";
import { USER_STATE } from '@/states/IUserState'
import { FormDrawer } from '@kinotic-ai/frontend-common'
import characterUrl from '@/assets/kinotic-character-tile.svg'

const debug = createDebug('application-sidebar');

// Long enough to read the status view even when the server answers instantly
const MIN_CREATING_MS = 700
// How long the finished status stays up before the drawer closes
const READY_HOLD_MS = 1200

interface ApplicationForm {
  name: string
  description: string
}

/** form: filling in the fields; creating: the request is in flight; ready: the server created it. */
type Phase = 'form' | 'creating' | 'ready'

interface SetupStep {
  label: string
  done: boolean
  /** The step the request is working on; later steps wait for it. */
  active: boolean
}

const props = defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  (e: 'submit', createdApplication: Application): void
  (e: 'close'): void
}>()

const toast = useToast()

const form = reactive<ApplicationForm>({
  name: '',
  description: ''
})

const phase = ref<Phase>('form')
const created = ref<Application | null>(null)

const isSubmitDisabled = computed(() => phase.value !== 'form' || form.name.trim() === '')

// Each step reflects what the one create request has actually done so far
const setupSteps = computed<SetupStep[]>(() => {
  const ready = phase.value === 'ready'
  return [
    { label: `Saving ${form.name.trim()}`, done: ready, active: !ready },
    { label: ready ? `Assigned id ${created.value?.id}` : 'Assigning its id', done: ready, active: false },
    { label: 'Ready', done: ready, active: false }
  ]
})

function resetForm(): void {
  form.name = ''
  form.description = ''
  phase.value = 'form'
  created.value = null
}

async function handleSubmit(): Promise<void> {
  phase.value = 'creating'
  try {
    // The server mints the id from the slugified name, so send an empty id.
    const applicationData: Application = {
      id: '',
      name: form.name.trim(),
      organizationId: USER_STATE.getOrganizationId(),
      description: form.description,
      tenantPerUser: false,
      updated: null
    }

    const [createdApplication] = await Promise.all([
      Kinotic.applications.createSync(applicationData),
      delay(MIN_CREATING_MS)
    ])
    created.value = createdApplication
    phase.value = 'ready'

    await delay(READY_HOLD_MS)
    finish()
  } catch (error) {
    debug('Failed to create application: %O', error)
    showErrorToast(toast, 'Failed to create application', error)
    phase.value = 'form'
  }
}

// Hands the created application to the page once, whether the hold ran out or the user closed early
function finish(): void {
  const createdApplication = created.value
  if (createdApplication) {
    resetForm()
    emit('submit', createdApplication)
  }
}

function handleClose(): void {
  if (phase.value === 'ready') {
    finish()
  } else if (phase.value === 'form') {
    resetForm()
    emit('close')
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}
</script>

<template>
  <FormDrawer
    :visible="props.visible"
    title="New application"
    description="An application groups the projects your organization builds and deploys."
    @close="handleClose"
  >
    <div class="flex h-full flex-col">
      <form v-if="phase === 'form'" id="new-application-form" class="flex flex-col gap-5" @submit.prevent="handleSubmit">
        <div>
          <label for="new-application-name" class="mb-2 block text-sm font-medium">Name</label>
          <InputText id="new-application-name" v-model="form.name" type="text" class="w-full" required autofocus />
          <p class="mt-1.5 text-[0.8125rem] text-surface-500 dark:text-surface-400">The application's id is derived from this name.</p>
        </div>
        <div>
          <label for="new-application-description" class="mb-2 block text-sm font-medium">Description</label>
          <Textarea id="new-application-description" v-model="form.description" class="w-full" rows="3" />
          <p class="mt-1.5 text-[0.8125rem] text-surface-500 dark:text-surface-400">Optional. Shown in the application list.</p>
        </div>
      </form>

      <div class="flex flex-1 flex-col items-center justify-center gap-5 py-8 text-center">
        <img :src="characterUrl" alt="" class="h-48 w-48" />

        <p v-if="phase === 'form'" class="max-w-xs text-sm text-surface-500 dark:text-surface-400">
          Give your application a name to get started.
        </p>

        <div v-else class="flex flex-col items-center gap-4" aria-live="polite">
          <p class="text-base font-medium text-surface-950 dark:text-surface-0">
            {{ phase === 'ready' ? `${form.name.trim()} is ready` : `Setting up ${form.name.trim()}` }}
          </p>
          <p v-if="phase === 'ready'" class="-mt-2 text-sm text-surface-500 dark:text-surface-400">Next, add its first project.</p>
          <ul class="flex flex-col gap-2 text-left">
            <li
              v-for="step in setupSteps"
              :key="step.label"
              :class="['flex items-center gap-2.5 font-mono text-[0.8125rem]', step.done ? 'text-surface-800 dark:text-surface-100' : 'text-surface-500 dark:text-surface-400']"
            >
              <Check v-if="step.done" :size="16" :stroke-width="2" class="text-green-600 dark:text-green-400" aria-hidden="true" />
              <LoaderCircle v-else-if="step.active" :size="16" :stroke-width="2" class="animate-spin" aria-hidden="true" />
              <Circle v-else :size="16" :stroke-width="2" class="opacity-40" aria-hidden="true" />
              {{ step.label }}
            </li>
          </ul>
        </div>
      </div>
    </div>

    <template #footer>
      <Button v-if="phase !== 'ready'" type="button" severity="secondary" variant="outlined" label="Cancel" :disabled="phase === 'creating'" @click="handleClose" />
      <Button
        v-if="phase === 'form'"
        type="submit"
        form="new-application-form"
        :disabled="isSubmitDisabled"
        label="Create application"
      />
      <Button v-else-if="phase === 'creating'" type="button" :loading="true" label="Creating…" />
      <Button v-else type="button" label="Add a project" @click="finish" />
    </template>
  </FormDrawer>
</template>
