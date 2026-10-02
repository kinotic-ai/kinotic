<template>
  <div>
    <div v-if="loading" class="flex justify-center py-12">
      <i class="pi pi-spin pi-spinner text-2xl text-muted-color"></i>
    </div>

    <FeatureEmptyState
      v-else-if="!customizing"
      badge="built-in"
      :icon="Mail"
      :tint="TINTS.sky"
      title="Make the invitation yours"
      description="This application sends Kinotic's built-in invitation email. Customize it to control what invitees receive."
      :points="[
        'Write your own subject, message and call to action',
        'Personalize it with the inviter, organization and application names',
        'Send HTML with a plain-text version for every mail client'
      ]"
    >
      <template #preview>
        <div class="rounded-xl border border-surface-200 bg-surface-0 shadow-sm dark:border-surface-700 dark:bg-surface-900">
          <div class="flex flex-col gap-1.5 border-b border-surface-100 px-4 py-3 text-[11px] dark:border-surface-800">
            <div class="flex gap-2"><span class="w-12 text-surface-400">From</span><span class="text-surface-700 dark:text-surface-200">Kinotic</span></div>
            <div class="flex gap-2"><span class="w-12 text-surface-400">Subject</span><span class="font-medium text-surface-950 dark:text-surface-0">You're invited to join {{ placeholderOf('applicationName') }}</span></div>
          </div>
          <div class="flex flex-col gap-2.5 px-4 py-4">
            <div class="h-2.5 w-3/4 rounded bg-surface-200 dark:bg-surface-700" />
            <div class="h-2.5 w-full rounded bg-surface-200 dark:bg-surface-700" />
            <div class="h-2.5 w-2/3 rounded bg-surface-200 dark:bg-surface-700" />
            <span class="mt-2 w-fit rounded-md bg-surface-950 px-3 py-1.5 text-[11px] font-medium text-surface-0 dark:bg-surface-0 dark:text-surface-950">Accept invitation</span>
            <div class="mt-1 h-2 w-1/2 rounded bg-surface-200 dark:bg-surface-700" />
          </div>
        </div>
      </template>
      <template #actions>
        <Button label="Customize" icon="pi pi-pencil" @click="startCustomizing" />
      </template>
    </FeatureEmptyState>

    <form v-else class="grid items-start gap-4 pt-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]" @submit.prevent="save">
      <section class="overflow-hidden rounded-xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-5 p-5">
          <div>
            <label for="tpl-subject" :class="LABEL_CLASS">Subject</label>
            <InputText id="tpl-subject" v-model="subject" class="mt-2 w-full" />
          </div>

          <div>
            <div class="flex items-center justify-between gap-3">
              <span :class="LABEL_CLASS">Body</span>
              <SelectButton v-model="bodyFormat" :options="BODY_FORMATS" option-label="label" option-value="value" :allow-empty="false" size="small" />
            </div>
            <TemplateCodeEditor v-if="bodyFormat === 'html'" id="tpl-html" v-model="htmlBody" html
                                file-name="invitation.html" aria-label="HTML body" class="mt-2" />
            <TemplateCodeEditor v-else id="tpl-text" v-model="textBody"
                                file-name="invitation.txt" aria-label="Plain-text body" class="mt-2" />
            <p :class="HELP_CLASS">
              <template v-if="bodyFormat === 'html'">What most mail clients show. Put <code v-pre class="rounded-sm bg-indigo-50 font-mono text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">{{{acceptUrl}}}</code>, with triple braces, inside links so the URL isn't HTML-escaped.</template>
              <template v-else>For mail clients that don't render HTML.</template>
            </p>
          </div>

          <!-- framed like the code editor above, with each variable in the colour the editor marks it in -->
          <div class="overflow-hidden rounded-lg border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-950">
            <div class="flex items-center justify-between gap-3 border-b border-surface-200 bg-surface-50 px-3 py-2 dark:border-surface-700 dark:bg-surface-900">
              <span class="flex items-center gap-2 text-[13px] text-surface-700 dark:text-surface-200">
                <Braces :size="15" :stroke-width="1.75" class="shrink-0 text-surface-500" aria-hidden="true" />
                Variables
              </span>
              <span class="truncate text-xs text-muted-color">Handlebars · click to copy</span>
            </div>
            <div class="flex flex-wrap gap-2 px-4 py-3">
              <button v-for="variable in VARIABLES" :key="variable" type="button"
                      class="group inline-flex items-center gap-1.5 rounded-md bg-indigo-50 px-2 py-1 font-mono text-[13px] leading-5 text-indigo-700 ring-1 ring-inset ring-indigo-100 transition-colors hover:bg-indigo-100 hover:ring-indigo-200 dark:bg-indigo-500/15 dark:text-indigo-300 dark:ring-indigo-500/20 dark:hover:bg-indigo-500/25"
                      v-tooltip.top="copied === variable ? 'Copied' : 'Copy'"
                      @click="copyVariable(variable)">
                {{ placeholderOf(variable) }}
                <Check v-if="copied === variable" :size="12" :stroke-width="2" class="text-green-600 dark:text-green-400" aria-hidden="true" />
                <Copy v-else :size="12" :stroke-width="1.75" class="text-indigo-400 group-hover:text-indigo-600 dark:text-indigo-400/70 dark:group-hover:text-indigo-300" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 border-t border-surface-200 bg-surface-50 px-5 py-3 dark:border-surface-700 dark:bg-surface-900/40">
          <Button
            v-if="savedTemplateId"
            label="Revert to built-in"
            severity="danger"
            outlined
            @click="confirmRevert"
          />
          <Button v-else label="Cancel" severity="secondary" outlined @click="customizing = false" />
          <Button type="submit" label="Save" :loading="saving" />
        </div>
      </section>

      <!-- How the email reads in an inbox, with example values in place of the variables -->
      <section class="feature-preview-canvas overflow-hidden rounded-xl border border-surface-200 p-5 dark:border-surface-700">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-surface-500">Preview</span>
          <span class="text-xs text-muted-color">with example values</span>
        </div>
        <div class="overflow-hidden rounded-xl border border-surface-200 bg-surface-0 shadow-sm dark:border-surface-700 dark:bg-surface-900">
          <div class="flex items-start gap-3 border-b border-surface-100 px-4 py-3 dark:border-surface-800">
            <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-950 dark:ring-1 dark:ring-surface-700">
              <img :src="kinoticLogo" alt="" class="h-3.5 w-4" />
            </span>
            <div class="min-w-0">
              <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0">{{ renderedSubject || 'No subject' }}</div>
              <div class="text-xs text-muted-color">Kinotic · to {{ SAMPLE.inviteeEmail }}</div>
            </div>
          </div>
          <!-- The HTML renders in a sandboxed frame, so the template's markup and styles stay out of the portal;
               scripts stay blocked, and same-origin only keeps the frame renderable in-process -->
          <iframe v-if="bodyFormat === 'html'" :srcdoc="renderedHtml" sandbox="allow-same-origin" title="HTML body preview"
                  class="h-[360px] w-full bg-white" />
          <pre v-else class="m-0 h-[360px] overflow-auto whitespace-pre-wrap px-4 py-4 font-mono text-[13px] leading-6 text-surface-800 dark:text-surface-100">{{ renderedText }}</pre>
        </div>
      </section>
    </form>

    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Braces, Check, Copy, Mail } from '@lucide/vue'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { InviteEmailTemplate } from '@kinotic-ai/management-api'
import { showErrorToast, TINTS } from '@kinotic-ai/frontend-common'
import FeatureEmptyState from '@/components/FeatureEmptyState.vue'
import TemplateCodeEditor from '@/components/TemplateCodeEditor.vue'
import kinoticLogo from '@/assets/header-logo.svg'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { PROFILE_STATE } from '@/states/IProfileState'
import { USER_STATE } from '@/states/IUserState'

/**
 * Editor for an application's customized invitation email. Without a saved template the
 * application uses the built-in email; saving validates the Handlebars sources on the
 * server, and reverting deletes the template.
 */
const props = defineProps<{
  applicationId: string
}>()

const loading = ref(true)
const customizing = ref(false)
const saving = ref(false)
const savedTemplateId = ref<string | null>(null)
const subject = ref('')
const htmlBody = ref('')
const textBody = ref('')

const toast = useToast()

const LABEL_CLASS = 'block text-sm font-medium text-surface-950 dark:text-surface-0'
const HELP_CLASS = 'mt-1 text-xs leading-5 text-muted-color'

const VARIABLES = ['inviterName', 'organizationName', 'applicationName', 'acceptUrl', 'expiresInDays'] as const
type Variable = typeof VARIABLES[number]

const BODY_FORMATS = [{ label: 'HTML', value: 'html' }, { label: 'Plain text', value: 'text' }]
const bodyFormat = ref<'html' | 'text'>('html')
const copied = ref<Variable | null>(null)

/** Example values the preview shows in place of the variables, from the signed-in user where there is one. */
const SAMPLE = {
  inviteeEmail: 'teammate@example.com',
  values: computed<Record<Variable, string>>(() => ({
    inviterName: PROFILE_STATE.profile?.displayName || PROFILE_STATE.profile?.email || 'Ava Chen',
    organizationName: USER_STATE.getOrganizationId() || 'Your organization',
    applicationName: APPLICATION_STATE.currentApplication?.name || props.applicationId,
    acceptUrl: 'https://example.com/invitations/accept',
    expiresInDays: '7'
  }))
}

function placeholderOf(variable: Variable): string {
  return `{{${variable}}}`
}

async function copyVariable(variable: Variable): Promise<void> {
  await navigator.clipboard.writeText(placeholderOf(variable))
  copied.value = variable
  setTimeout(() => {
    if (copied.value === variable) {
      copied.value = null
    }
  }, 1500)
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

// Only the variables are filled in, as Handlebars would: {{x}} escaped for HTML, {{{x}}} as is.
// Anything else Handlebars supports shows as written; the server validates the template on save.
function render(template: string, escape: boolean): string {
  const values = SAMPLE.values.value
  return template
      .replace(/\{\{\{\s*(\w+)\s*\}\}\}/g, (match, name: string) => name in values ? values[name as Variable] : match)
      .replace(/\{\{\s*(\w+)\s*\}\}/g, (match, name: string) => {
        const value = name in values ? values[name as Variable] : undefined
        return value === undefined ? match : (escape ? escapeHtml(value) : value)
      })
}

const renderedSubject = computed(() => render(subject.value, false))
const renderedText = computed(() => render(textBody.value, false))
const renderedHtml = computed(() =>
    `<!doctype html><html><body style="margin:0;padding:16px;font:14px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#18181b">${render(htmlBody.value, true)}</body></html>`)
const confirm = useConfirm()

onMounted(async () => {
  try {
    const template = await Kinotic.inviteEmailTemplates.findByApplication(props.applicationId)
    if (template) {
      savedTemplateId.value = template.id
      subject.value = template.subject
      htmlBody.value = template.htmlBody
      textBody.value = template.textBody
      customizing.value = true
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to load template', err, { life: 8000 })
  } finally {
    loading.value = false
  }
})

function startCustomizing() {
  // Working scaffold so the first save succeeds; every field can be rewritten.
  if (!subject.value && !htmlBody.value && !textBody.value) {
    subject.value = "You're invited to join {{applicationName}}"
    htmlBody.value = [
      '<p>{{inviterName}} has invited you to join {{applicationName}}.</p>',
      '<p><a href="{{{acceptUrl}}}">Accept the invitation</a></p>',
      '<p>This invitation expires in {{expiresInDays}} days.</p>'
    ].join('\n')
    textBody.value = [
      '{{inviterName}} has invited you to join {{applicationName}}.',
      '',
      'Accept the invitation: {{acceptUrl}}',
      '',
      'This invitation expires in {{expiresInDays}} days.'
    ].join('\n')
  }
  customizing.value = true
}

async function save() {
  saving.value = true
  try {
    const template = new InviteEmailTemplate()
    template.id = savedTemplateId.value
    template.applicationId = props.applicationId
    template.subject = subject.value
    template.htmlBody = htmlBody.value
    template.textBody = textBody.value

    const saved = await Kinotic.inviteEmailTemplates.save(template)
    savedTemplateId.value = saved.id
    toast.add({ severity: 'success', summary: 'Template saved', life: 4000 })
  } catch (err) {
    // Server-side Handlebars validation messages carry the parse position.
    showErrorToast(toast, 'Failed to save template', err, { life: 10000 })
  } finally {
    saving.value = false
  }
}

function confirmRevert() {
  confirm.require({
    header: 'Revert to built-in email',
    message: 'Delete this template? Invitations for this application go back to the built-in email.',
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revert', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => revert()
  })
}

async function revert() {
  if (!savedTemplateId.value) return
  try {
    await Kinotic.inviteEmailTemplates.deleteById(savedTemplateId.value)
    savedTemplateId.value = null
    subject.value = ''
    htmlBody.value = ''
    textBody.value = ''
    customizing.value = false
    toast.add({ severity: 'success', summary: 'Reverted to the built-in email', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to revert', err, { life: 8000 })
  }
}

</script>

<style scoped>
.feature-preview-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-400) 16%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, var(--p-sky-50), color-mix(in srgb, var(--p-indigo-50) 60%, transparent), color-mix(in srgb, var(--p-violet-50) 70%, transparent));
  background-size: 14px 14px, 100% 100%;
}

.dark .feature-preview-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-500) 14%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, color-mix(in srgb, var(--p-sky-500) 10%, transparent), color-mix(in srgb, var(--p-indigo-500) 5%, transparent), color-mix(in srgb, var(--p-violet-500) 10%, transparent));
}
</style>
