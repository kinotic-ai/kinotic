<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { createDebug } from '@/util/debug'
import type {Application} from "@kinotic-ai/os-api";
import {Kinotic} from "@kinotic-ai/core";
import { USER_STATE } from '@/states/IUserState'
import { isDark as darkMode } from '@/composables/useTheme'

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

const loading = ref(false)
const isDark = darkMode
const inputClass = computed(() => [
  'w-full !shadow-none',
  isDark.value
    ? 'border-surface-700 bg-surface-800 text-surface-0 placeholder:text-surface-400 focus:border-surface-600'
    : 'border-surface-300 bg-surface-0 text-surface-950 placeholder:text-surface-400'
])

const isSubmitDisabled = computed(() => loading.value || form.name.trim() === '')

function sanitizeId(name: string): string {
  let sanitized = name
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^a-zA-Z0-9._-]/g, '')

  if (!/^[a-zA-Z]/.test(sanitized)) {
    sanitized = 'app-' + sanitized
  }

  return sanitized.toLowerCase()
}

function resetForm(): void {
  form.name = ''
  form.description = ''
}

async function handleSubmit(): Promise<void> {
  loading.value = true
  try {
    const applicationData: Application = {
      id: sanitizeId(form.name),
      organizationId: USER_STATE.getOrganizationId(),
      description: form.description,
      updated: null
    }

    const createdApplication = await Kinotic.applications.create(applicationData)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Application successfully added',
      life: 3000
    })

    resetForm()
    emit('submit', createdApplication)
  } catch (error) {
    debug('Failed to create application: %O', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to create application. Please check name validity.',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

function handleClose(): void {
  resetForm()
  emit('close')
}
</script>

<template>
  <transition
    enter-active-class="transition-transform duration-300 ease-out"
    enter-from-class="translate-x-full"
    enter-to-class="translate-x-0"
    leave-active-class="transition-transform duration-300 ease-in"
    leave-from-class="translate-x-0"
    leave-to-class="translate-x-full"
  >
    <div v-if="props.visible" :class="['fixed inset-y-0 right-0 z-50 h-screen w-[400px] overflow-y-auto shadow-xl', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
      <div :class="['mb-4 flex items-center justify-between border-b p-4', isDark ? 'border-b-surface-800' : 'border-b-surface-200']">
        <div class="flex items-center gap-3">
          <div :class="['flex h-[35px] w-[35px] shrink-0 items-center justify-center rounded-[8px]', isDark ? 'bg-surface-800' : 'bg-surface-100']">
            <img src="@/assets/plus.svg" alt="Create application" class="h-6 w-6" />
          </div>
          <h2 class="text-lg font-semibold">New Application</h2>
        </div>
        <span @click="handleClose" class="w-[11px] h-[11px] cursor-pointer">
          <img src="@/assets/close-icon.svg" />
        </span>
      </div>
      <form @submit.prevent="handleSubmit" class="flex flex-col h-[calc(100vh-100px)] justify-between gap-4 p-4">
        <div>
          <div class="mb-5">
            <label :class="['mb-3 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Name</label>
            <InputText v-model="form.name" type="text" :class="inputClass" required />
          </div>
          <div class="mb-5">
            <label :class="['mb-3 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Description</label>
            <Textarea v-model="form.description" :class="inputClass" rows="3" />
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
