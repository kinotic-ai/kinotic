<template>
  <div class="invite-email-page">
    <h1 class="invite-email-page__title">Invitation email</h1>

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

    <form v-else class="invite-email-page__form flex flex-col gap-4" @submit.prevent="save">
      <div class="flex flex-col gap-1">
        <label for="tpl-subject" class="text-sm font-medium">Subject</label>
        <InputText id="tpl-subject" v-model="subject" class="w-full" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="tpl-html" class="text-sm font-medium">HTML body</label>
        <Textarea id="tpl-html" v-model="htmlBody" class="w-full invite-email-page__source" rows="14" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="tpl-text" class="text-sm font-medium">Plain-text body</label>
        <Textarea id="tpl-text" v-model="textBody" class="w-full invite-email-page__source" rows="8" />
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

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { InviteEmailTemplate } from '@kinotic-ai/os-api'

/**
 * Editor for an application's customized invitation email. Without a saved template the
 * application uses the built-in email; saving validates the Handlebars sources on the
 * server, and reverting deletes the template.
 */
@Component({
  components: { Button, ConfirmDialog, InputText, Textarea }
})
export default class InviteEmailTemplatePage extends Vue {
  @Prop({ required: true }) applicationId!: string

  loading = true
  customizing = false
  saving = false
  savedTemplateId: string | null = null
  subject = ''
  htmlBody = ''
  textBody = ''

  private toast = useToast()
  private confirm = useConfirm()

  async mounted() {
    try {
      const template = await Kinotic.inviteEmailTemplates.findByApplication(this.applicationId)
      if (template) {
        this.savedTemplateId = template.id
        this.subject = template.subject
        this.htmlBody = template.htmlBody
        this.textBody = template.textBody
        this.customizing = true
      }
    } catch (err) {
      this.toast.add({ severity: 'error', summary: 'Error', detail: this.message(err, 'Failed to load template'), life: 8000 })
    } finally {
      this.loading = false
    }
  }

  startCustomizing() {
    // Working scaffold so the first save succeeds; every field can be rewritten.
    if (!this.subject && !this.htmlBody && !this.textBody) {
      this.subject = "You're invited to join {{applicationName}}"
      this.htmlBody = [
        '<p>{{inviterName}} has invited you to join {{applicationName}}.</p>',
        '<p><a href="{{{acceptUrl}}}">Accept the invitation</a></p>',
        '<p>This invitation expires in {{expiresInDays}} days.</p>'
      ].join('\n')
      this.textBody = [
        '{{inviterName}} has invited you to join {{applicationName}}.',
        '',
        'Accept the invitation: {{acceptUrl}}',
        '',
        'This invitation expires in {{expiresInDays}} days.'
      ].join('\n')
    }
    this.customizing = true
  }

  async save() {
    this.saving = true
    try {
      const template = new InviteEmailTemplate()
      template.id = this.savedTemplateId
      template.applicationId = this.applicationId
      template.subject = this.subject
      template.htmlBody = this.htmlBody
      template.textBody = this.textBody

      const saved = await Kinotic.inviteEmailTemplates.save(template)
      this.savedTemplateId = saved.id
      this.toast.add({ severity: 'success', summary: 'Template saved', life: 4000 })
    } catch (err) {
      // Server-side Handlebars validation messages carry the parse position.
      this.toast.add({ severity: 'error', summary: 'Error', detail: this.message(err, 'Failed to save template'), life: 10000 })
    } finally {
      this.saving = false
    }
  }

  confirmRevert() {
    this.confirm.require({
      header: 'Revert to built-in email',
      message: 'Delete this template? Invitations for this application go back to the built-in email.',
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Revert', severity: 'danger' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: () => this.revert()
    })
  }

  private async revert() {
    if (!this.savedTemplateId) return
    try {
      await Kinotic.inviteEmailTemplates.deleteById(this.savedTemplateId)
      this.savedTemplateId = null
      this.subject = ''
      this.htmlBody = ''
      this.textBody = ''
      this.customizing = false
      this.toast.add({ severity: 'success', summary: 'Reverted to the built-in email', life: 4000 })
    } catch (err) {
      this.toast.add({ severity: 'error', summary: 'Error', detail: this.message(err, 'Failed to revert'), life: 8000 })
    }
  }

  private message(err: unknown, fallback: string): string {
    return err instanceof Error && err.message ? err.message : fallback
  }
}
</script>

<style scoped>
.invite-email-page__title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 1rem;
}

.invite-email-page__form {
  max-width: 56rem;
}

.invite-email-page__source {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
}
</style>
