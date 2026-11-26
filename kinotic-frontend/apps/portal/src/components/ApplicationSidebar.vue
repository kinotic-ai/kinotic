<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { createDebug } from '@kinotic-ai/frontend-common'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import {type Application, TenantManagement} from "@kinotic-ai/management-api";
import {Kinotic} from "@kinotic-ai/core";
import { USER_STATE } from '@/states/IUserState'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import BetaPlanSummary from '@/components/BetaPlanSummary.vue'
import { FormDrawer } from '@kinotic-ai/frontend-common'
import CreationStatus from '@/components/CreationStatus.vue'
import { useCreationPhase } from '@/composables/useCreationPhase'

const debug = createDebug('application-sidebar');

interface ApplicationForm {
  name: string
  description: string
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

const { phase, create, holdReady } = useCreationPhase()
const created = ref<Application | null>(null)

// The beta plan's application cap is reached, so the drawer explains the plan instead of offering the form
const atApplicationLimit = computed(() => {
  const max = APPLICATION_STATE.betaLimits?.maxApplicationsPerOrganization ?? null
  return phase.value === 'form' && max !== null && (APPLICATION_STATE.betaLimits?.applicationCount ?? 0) >= max
})

const isSubmitDisabled = computed(() => phase.value !== 'form' || form.name.trim() === '' || atApplicationLimit.value)

// Each step reflects what the one create request has actually done so far
const setupSteps = computed(() => {
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
  try {
    // The server mints the id from the slugified name, so send an empty id.
    const applicationData: Application = {
      id: '',
      name: form.name.trim(),
      organizationId: USER_STATE.getOrganizationId(),
      description: form.description,
      tenantManagement: TenantManagement.TENANT_ADMINISTRATORS,
      primaryUiId: null,
      primaryUiUrl: null,
      updated: null
    }

    created.value = await create(() => Kinotic.applications.createSync(applicationData))
    await holdReady()
    finish()
  } catch (error) {
    debug('Failed to create application: %O', error)
    // A beta-limit refusal explains what to do instead, so it stays up long enough to read
    showErrorToast(toast, 'Failed to create application', error, { life: 15000 })
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
</script>

<template>
  <FormDrawer
    :visible="props.visible"
    title="New application"
    description="An application groups the projects your organization builds and deploys."
    @close="handleClose"
  >
    <div v-if="atApplicationLimit" class="flex flex-col gap-4">
      <div class="rounded-xl border border-amber-200 bg-amber-50/70 px-4 py-3.5 text-[0.8125rem] text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300"
           role="status">
        Your organization has all the applications the free beta allows. Build in one of your existing
        applications, or delete one you no longer need to make room.
      </div>
      <BetaPlanSummary />
    </div>
    <div v-else class="flex h-full flex-col">
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

      <CreationStatus
        :phase="phase"
        :name="form.name.trim()"
        hint="Give your application a name to get started."
        next-step="Next, add its first project."
        :steps="setupSteps"
      />
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
