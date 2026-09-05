<template>
  <div class="invite-email-page">
    <div v-if="loading" class="flex justify-center py-12">
      <i class="pi pi-spin pi-spinner text-3xl text-primary-500"></i>
    </div>

    <div v-else-if="!customizing" class="invite-email-page__empty">
      <p class="text-muted-color mb-4">
        This application uses the built-in invitation email. Customize it to control what
        invitees receive.
      </p>
      <Button label="Customize" icon="pi pi-pencil" @click="startCustomizing" />
    </div>

    <form v-else class="max-w-4xl flex flex-col gap-4" @submit.prevent="save">
      <div class="flex flex-col gap-1">
        <label for="tpl-subject" class="text-sm font-medium">Subject</label>
        <InputText id="tpl-subject" v-model="subject" class="w-full" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="tpl-html" class="text-sm font-medium">HTML body</label>
        <Textarea id="tpl-html" v-model="htmlBody" class="w-full font-mono text-sm" rows="14" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="tpl-text" class="text-sm font-medium">Plain-text body</label>
        <Textarea id="tpl-text" v-model="textBody" class="w-full font-mono text-sm" rows="8" />
      </div>

      <p class="text-sm text-muted-color m-0">
        Templates are Handlebars. Available variables:
        <code v-pre>{{inviterName}}</code>, <code v-pre>{{organizationName}}</code>,
        <code v-pre>{{applicationName}}</code>, <code v-pre>{{acceptUrl}}</code>,
        <code v-pre>{{expiresInDays}}</code>.
        In the HTML body use <code v-pre>{{{acceptUrl}}}</code> (triple braces) inside links so
        the URL isn't HTML-escaped.
      </p>

      <div class="flex items-center gap-2">
        <Button type="submit" label="Save" :loading="saving" />
        <Button
          v-if="savedTemplateId"
          label="Revert to built-in"
          severity="danger"
          outlined
          @click="confirmRevert"
        />
        <Button v-else label="Cancel" severity="secondary" outlined @click="customizing = false" />
      </div>
    </form>

    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { InviteEmailTemplate } from '@kinotic-ai/management-api'
import { showErrorToast } from '@kinotic-ai/frontend-common'

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

