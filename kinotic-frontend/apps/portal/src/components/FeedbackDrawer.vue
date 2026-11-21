<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import SelectButton from 'primevue/selectbutton'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import { type FeedbackIssue, FeedbackSubmission, FeedbackType } from '@kinotic-ai/management-api'
import { createDebug, FormDrawer, showErrorToast } from '@kinotic-ai/frontend-common'

const debug = createDebug('feedback-drawer')

/**
 * Sends a bug report or feature request about Kinotic, which is filed as a public GitHub issue.
 * The page it is sent from rides along as its route pattern, which names no application or project.
 */
const props = defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const TITLE_MAX_LENGTH = 200

const TYPE_OPTIONS = [
  { label: 'Report a bug', value: FeedbackType.BUG_REPORT },
  { label: 'Request a feature', value: FeedbackType.FEATURE_REQUEST }
]

const route = useRoute()
const toast = useToast()

function emptyForm() {
  return {
    type: FeedbackType.BUG_REPORT,
    title: '',
    description: '',
    stepsToReproduce: '',
    expectedBehavior: '',
    actualBehavior: ''
  }
}

const form = reactive(emptyForm())

const sending = ref(false)
const sent = ref<FeedbackIssue | null>(null)

const isBug = computed(() => form.type === FeedbackType.BUG_REPORT)
const isSubmitDisabled = computed(() => sending.value || form.title.trim() === '' || form.description.trim() === '')
const pagePattern = computed(() => route.matched.at(-1)?.path ?? route.path)

function resetForm(): void {
  Object.assign(form, emptyForm())
  sent.value = null
}

function optional(text: string): string | null {
  return text.trim() === '' ? null : text.trim()
}

async function handleSubmit(): Promise<void> {
  const submission = new FeedbackSubmission()
  submission.type = form.type
  submission.title = form.title.trim()
  submission.description = form.description.trim()
  // the bug-only fields keep what was typed if the type was switched, so send them only for a bug
  submission.stepsToReproduce = isBug.value ? optional(form.stepsToReproduce) : null
  submission.expectedBehavior = isBug.value ? optional(form.expectedBehavior) : null
  submission.actualBehavior = isBug.value ? optional(form.actualBehavior) : null
  submission.location = `Portal page ${pagePattern.value}`

  sending.value = true
  try {
    sent.value = await Kinotic.feedback.send(submission)
  } catch (error) {
    debug('Failed to send feedback: %O', error)
    showErrorToast(toast, 'Failed to send feedback', error)
  } finally {
    sending.value = false
  }
}

function handleClose(): void {
  if (!sending.value) {
    resetForm()
    emit('close')
  }
}
</script>

<template>
  <FormDrawer
    :visible="props.visible"
    title="Send feedback"
    description="Tell the Kinotic team about a bug or a feature you need. It is posted as a public issue on GitHub."
    @close="handleClose"
  >
    <form v-if="!sent" id="feedback-form" class="flex flex-col gap-5" @submit.prevent="handleSubmit">
      <SelectButton v-model="form.type" :options="TYPE_OPTIONS" option-label="label" option-value="value"
                    :allow-empty="false" aria-label="Feedback type" />
      <div>
        <label for="feedback-title" class="mb-2 block text-sm font-medium">Title</label>
        <InputText id="feedback-title" v-model="form.title" type="text" class="w-full" :maxlength="TITLE_MAX_LENGTH" required autofocus />
      </div>
      <div>
        <label for="feedback-description" class="mb-2 block text-sm font-medium">
          {{ isBug ? 'What went wrong?' : 'What do you need, and why?' }}
        </label>
        <Textarea id="feedback-description" v-model="form.description" class="w-full" rows="5" required />
      </div>
      <template v-if="isBug">
        <div>
          <label for="feedback-steps" class="mb-2 block text-sm font-medium">Steps to reproduce</label>
          <Textarea id="feedback-steps" v-model="form.stepsToReproduce" class="w-full" rows="3" />
        </div>
        <div>
          <label for="feedback-expected" class="mb-2 block text-sm font-medium">What you expected</label>
          <Textarea id="feedback-expected" v-model="form.expectedBehavior" class="w-full" rows="2" />
        </div>
        <div>
          <label for="feedback-actual" class="mb-2 block text-sm font-medium">What happened instead</label>
          <Textarea id="feedback-actual" v-model="form.actualBehavior" class="w-full" rows="2" />
        </div>
      </template>
      <p class="text-[0.8125rem] text-surface-500 dark:text-surface-400">
        Anyone can read the issue, so leave out secrets, personal data and the names of your applications and
        projects. The page you are on ({{ pagePattern }}) is included, without your organization's ids.
      </p>
    </form>

    <div v-else class="flex flex-col gap-3">
      <p class="text-sm">Thanks. Your feedback is issue #{{ sent.number }}, where the Kinotic team will follow up.</p>
      <p class="text-sm text-surface-500 dark:text-surface-400">
        To add screenshots or logs, comment on the issue while signed in to GitHub.
      </p>
      <a :href="sent.url" target="_blank" rel="noopener" class="text-sm font-medium text-primary underline">
        Open the issue on GitHub
      </a>
    </div>

    <template #footer>
      <template v-if="!sent">
        <Button type="button" severity="secondary" variant="outlined" label="Cancel" :disabled="sending" @click="handleClose" />
        <Button type="submit" form="feedback-form" :disabled="isSubmitDisabled" :loading="sending" label="Send feedback" />
      </template>
      <Button v-else type="button" label="Done" @click="handleClose" />
    </template>
  </FormDrawer>
</template>
