<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import { Structures, type Application } from '@mindignited/structures-api'
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import Select from 'primevue/select';
import ToggleButton from 'primevue/togglebutton';
interface ApplicationForm {
  name: string
  description: string
  graphql: boolean
  openapi: boolean
}

@Component({
  components: {
    InputText,
    Textarea,
    Button,
    Select,
    ToggleButton
  }
})
export default class ApplicationSidebar extends Vue {
  @Prop({ required: true }) readonly visible!: boolean

  form: ApplicationForm = {
    name: '',
    description: '',
    graphql: true,
    openapi: false
  }

  loading = false
  get isSubmitDisabled(): boolean {
    return this.loading || this.form.name.trim() === '';
  }

  private sanitizeId(name: string): string {
    let sanitized = name.trim()
      .replace(/\s+/g, '-')
      .replace(/[^a-zA-Z0-9._-]/g, '')

    if (!/^[a-zA-Z]/.test(sanitized)) {
      sanitized = 'app-' + sanitized
    }
    return sanitized.toLowerCase()
  }

  async handleSubmit(): Promise<void> {
    this.loading = true
    try {
      const applicationData: Application = {
        id: this.sanitizeId(this.form.name),
        description: this.form.description,
        enableGraphQL: this.form.graphql,
        enableOpenAPI: this.form.openapi,
        updated: null
      }
      const createdApplication: Application = await Structures.getApplicationService().create(applicationData)

      this.$toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Application successfully added',
        life: 3000
      })

      this.resetForm()
      this.$emit('submit', createdApplication)
    } catch (error) {
      console.error('[ApplicationSidebar] Failed to create application:', error)
      this.$toast.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to create application. Please check name validity.',
        life: 3000
      })
    } finally {
      this.loading = false
    }
  }

  handleClose(): void {
    this.resetForm()
    this.$emit('close')
  }

  private resetForm(): void {
    this.form = {
      name: '',
      description: '',
      graphql: true,
      openapi: false
    }
  }
}
</script>

<template>
  <transition name="slide">
    <div v-if="visible" class="fixed inset-y-0 right-0 w-[400px] h-screen bg-white shadow-xl z-50 overflow-y-auto">
      <div class="flex justify-between items-center mb-4 border-b border-b-[#E6E7EB] p-4">
        <div class="flex items-center gap-3">
          <img src="@/assets/action-plus-icon.svg" />
          <h2 class="text-lg font-semibold">New Application</h2>
        </div>
        <span @click="handleClose" class="w-[11px] h-[11px] cursor-pointer">
          <img src="@/assets/close-icon.svg" />
        </span>
      </div>
      <form @submit.prevent="handleSubmit" class="flex flex-col h-[calc(100vh-100px)] justify-between gap-4 p-4">
        <div>
          <div class="mb-5">
            <label class="block text-sm text-[#101010] mb-3 font-semibold">Name</label>
            <InputText v-model="form.name" type="text" class="w-full" required />
          </div>
          <div class="mb-5">
            <label class="block text-sm text-[#101010] mb-3 font-semibold">Description</label>
            <Textarea v-model="form.description" class="w-full" rows="3" />
          </div>
          <div>
            <label class="block text-sm text-[#101010] mb-3 font-semibold">API configuration</label>
            <div class="border border-[#E6E7EB] rounded-2xl w-full divide-y divide-[#E6E7EB]">
              <div class="flex items-center justify-between p-4">
                <div class="flex items-center gap-2">
                  <img src="@/assets/graphql.svg" />
                  <span class="text-[#3F424D] text-sm font-normal">GraphQL</span>
                </div>
                <ToggleButton v-model="form.graphql" onLabel="On" offLabel="Off" />
              </div>
              <div class="flex items-center justify-between p-4">
                <div class="flex items-center gap-2">
                  <img src="@/assets/scalar.svg" />
                  <span class="text-[#3F424D] text-sm font-normal">OpenAPI</span>
                </div>
                <ToggleButton v-model="form.openapi" onLabel="On" offLabel="Off" />
              </div>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-6">
          <Button type="button" @click="handleClose" severity="secondary" label="Cancel" />
          <Button type="submit" :disabled="isSubmitDisabled" severity="primary" label="Create Application" />
        </div>
      </form>
    </div>
  </transition>
</template>