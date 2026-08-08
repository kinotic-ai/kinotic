<template>
  <div :class="['application-settings', isDark ? 'application-settings--dark' : 'application-settings--light']">
    <h1 class="application-settings__title">Application settings</h1>

    <div class="application-settings__general-shell">
      <form @submit.prevent="saveSettings" class="application-settings__form">
        <div class="application-settings__fields">
          <div class="application-settings__field">
            <label class="application-settings__label">Name</label>
            <InputText
              v-model="appName"
              type="text"
              class="application-settings__input w-full"
              disabled
            />
          </div>
          <div class="application-settings__field">
            <label class="application-settings__label">Description</label>
            <Textarea
              v-model="appDescription"
              class="application-settings__input application-settings__textarea w-full h-[100px]"
              rows="3"
            />
          </div>
          <div class="application-settings__field">
            <label class="application-settings__label">Tenant per user</label>
            <div class="flex items-center gap-3">
              <ToggleSwitch v-model="tenantPerUser" />
              <span class="text-sm text-muted-color">
                Each user of this application gets their own isolated tenant.
                Applies to users created after enabling.
              </span>
            </div>
          </div>
        </div>
        <div class="application-settings__actions">
          <Button
            class="application-settings__save-btn"
            type="submit"
            :disabled="loading"
            severity="primary"
            label="Save changes"
          />
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
// @ts-ignore
import { ref, defineProps, onMounted, watch } from 'vue'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import { InputText, Textarea, Button, ToggleSwitch } from 'primevue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'
import { Kinotic } from '@kinotic-ai/core'
import { useToast } from 'primevue/usetoast'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

defineProps({
  applicationId: {
    type: String,
    required: true
  }
})

const toast = useToast()
const appName = ref('')
const appDescription = ref('')
const tenantPerUser = ref(false)
const loading = ref(false)
const isDark = darkMode

watch(() => APPLICATION_STATE.currentApplication, (newApp) => {
  if (newApp) {
    appName.value = newApp.id || ''
    appDescription.value = newApp.description || ''
    tenantPerUser.value = Boolean(newApp.tenantPerUser)
  }
}, { immediate: true })

onMounted(() => {
  if (APPLICATION_STATE.currentApplication) {
    const app = APPLICATION_STATE.currentApplication
    appName.value = app.id || ''
    appDescription.value = app.description || ''
    tenantPerUser.value = Boolean(app.tenantPerUser)
  }
})

const saveSettings = async () => {
  if (!APPLICATION_STATE.currentApplication) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'No application selected',
      life: 3000
    })
    return
  }

  loading.value = true
  try {
    const updatedApplication = {
      ...APPLICATION_STATE.currentApplication,
      organizationId: USER_STATE.getOrganizationId(),
      description: appDescription.value,
      tenantPerUser: tenantPerUser.value
    }

    await Kinotic.applications.save(updatedApplication)
    
    APPLICATION_STATE.currentApplication = updatedApplication

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Application settings saved successfully',
      life: 3000
    })
  } catch (error) {
    showErrorToast(toast, 'Failed to save application settings', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.application-settings {
  transition: color 0.2s ease, background-color 0.2s ease;
}

.application-settings--dark {
  color: #ffffff;
}

.application-settings--light {
  color: #101010;
}

.application-settings__title {
  margin: 0 0 1.25rem;
  font-size: 1.5rem;
  font-weight: 600;
  line-height: 1;
}

.application-settings--dark .application-settings__title,
.application-settings--dark .application-settings__label {
  color: #ffffff;
}

.application-settings--light .application-settings__title,
.application-settings--light .application-settings__label {
  color: #101010;
}

.application-settings__general-shell {
  display: flex;
  justify-content: center;
  padding-top: 1.75rem;
}

.application-settings__form {
  width: 100%;
  max-width: 304px;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.application-settings__fields {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.application-settings__field {
  margin-bottom: 0;
}

.application-settings__label {
  display: block;
  margin-bottom: 0.75rem;
  font-size: 14px;
  font-weight: 600;
  line-height: 14px;
  letter-spacing: 0;
}

.application-settings__actions {
  display: flex;
  justify-content: flex-start;
  padding-top: 1.5rem;
}

.application-settings--dark :deep(.p-inputtext),
.application-settings--dark :deep(.p-textarea) {
  border: 1px solid #525252;
  background: transparent;
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(18, 18, 23, 0.05);
}

.application-settings--dark :deep(.p-inputtext) {
  min-height: 33px;
  padding: 8px 12px;
  background: #262626;
}

.application-settings--dark :deep(.p-textarea) {
  padding: 8px 12px;
  resize: none;
  box-shadow: none;
}

.application-settings--dark :deep(.p-inputtext:disabled) {
  border-color: #525252;
  background: #262626;
  color: #a3a3a3;
  -webkit-text-fill-color: #a3a3a3;
  opacity: 1;
}

.application-settings--dark :deep(.p-inputtext::placeholder),
.application-settings--dark :deep(.p-textarea::placeholder) {
  color: #a3a3a3;
}

.application-settings--light :deep(.p-inputtext),
.application-settings--light :deep(.p-textarea) {
  border: 1px solid #d9dce4;
  background: transparent;
  color: #101010;
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(18, 18, 23, 0.05);
}

.application-settings--light :deep(.p-inputtext) {
  background: #ffffff;
}

.application-settings--light :deep(.p-inputtext:disabled) {
  background: #e8eaf0;
  color: #71717a;
  opacity: 1;
}

.application-settings :deep(.p-inputtext:focus),
.application-settings :deep(.p-textarea:focus) {
  border-color: #52525b;
  box-shadow: none;
}

.application-settings :deep(.p-button.application-settings__save-btn) {
  min-width: 12.25rem;
  width: 100%;
  justify-content: center;
  border: none;
  border-radius: 0.5rem;
  background: var(--p-primary-500);
  color: #ffffff;
  box-shadow: none;
}

.application-settings :deep(.p-button.application-settings__save-btn:hover),
.application-settings :deep(.p-button.application-settings__save-btn:focus),
.application-settings :deep(.p-button.application-settings__save-btn:focus-visible) {
  border: none;
  background: var(--p-primary-600);
  box-shadow: none;
}
</style>
