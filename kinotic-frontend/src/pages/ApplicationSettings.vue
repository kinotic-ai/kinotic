<template>
  <div :class="['application-settings min-h-full p-4', isDark ? 'application-settings--dark' : 'application-settings--light']">
    <h1 class="application-settings__title">Application settings</h1>

    <Tabs class="application-settings__tabs" :value="activeTab" @update:value="(value: string | number) => activeTab = Number(value)">
      <TabList>
        <Tab :value="0">General</Tab>
        <Tab :value="1">Saved widgets</Tab>
      </TabList>
      <TabPanels class="!p-0">
        <TabPanel :value="0">
          <div v-show="activeTab === 0">
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
                    <label class="application-settings__label">API configuration</label>
                    <div class="application-settings__api-card">
                      <div class="application-settings__api-row">
                        <div class="application-settings__api-meta">
                          <img src="@/assets/graphql.svg" />
                          <span class="application-settings__api-name">GraphQL</span>
                        </div>
                        <button
                          type="button"
                          :class="['application-settings__switch', { 'application-settings__switch--on': enableGraphQL }]"
                          role="switch"
                          :aria-pressed="enableGraphQL"
                          :aria-checked="enableGraphQL"
                          aria-label="Toggle GraphQL"
                          @click="enableGraphQL = !enableGraphQL"
                        >
                          <span class="application-settings__switch-shell">
                            <span
                              v-if="enableGraphQL"
                              class="application-settings__switch-indicator"
                            ></span>
                            <span class="application-settings__switch-label">{{ enableGraphQL ? 'On' : 'Off' }}</span>
                          </span>
                        </button>
                      </div>
                      <div class="application-settings__api-row">
                        <div class="application-settings__api-meta">
                          <img src="@/assets/scalar.svg" />
                          <span class="application-settings__api-name">OpenAPI</span>
                        </div>
                        <button
                          type="button"
                          :class="['application-settings__switch', { 'application-settings__switch--on': enableOpenAPI }]"
                          role="switch"
                          :aria-pressed="enableOpenAPI"
                          :aria-checked="enableOpenAPI"
                          aria-label="Toggle OpenAPI"
                          @click="enableOpenAPI = !enableOpenAPI"
                        >
                          <span class="application-settings__switch-shell">
                            <span
                              v-if="enableOpenAPI"
                              class="application-settings__switch-indicator"
                            ></span>
                            <span class="application-settings__switch-label">{{ enableOpenAPI ? 'On' : 'Off' }}</span>
                          </span>
                        </button>
                      </div>
                      <div class="application-settings__api-row">
                        <div class="application-settings__api-meta">
                          <img src="@/assets/mcp.svg" />
                          <span class="application-settings__api-name">MCP (Model Context Protocol)</span>
                        </div>
                        <button
                          type="button"
                          :class="['application-settings__switch', { 'application-settings__switch--on': enableMCP }]"
                          role="switch"
                          :aria-pressed="enableMCP"
                          :aria-checked="enableMCP"
                          aria-label="Toggle MCP"
                          @click="enableMCP = !enableMCP"
                        >
                          <span class="application-settings__switch-shell">
                            <span
                              v-if="enableMCP"
                              class="application-settings__switch-indicator"
                            ></span>
                            <span class="application-settings__switch-label">{{ enableMCP ? 'On' : 'Off' }}</span>
                          </span>
                        </button>
                      </div>
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
        </TabPanel>
        <TabPanel :value="1">
          <div v-show="activeTab === 1">
            <!-- Loading state -->
            <div v-if="loadingWidgets" class="flex justify-center py-12">
              <i class="pi pi-spin pi-spinner text-3xl text-primary-500"></i>
            </div>

            <!-- Empty state -->
            <div v-else-if="savedWidgets.length === 0" class="text-center py-12">
              <div class="mb-4">
                <i class="pi pi-chart-bar text-6xl text-surface-300"></i>
              </div>
              <h3 class="text-lg font-semibold text-surface-800 mb-2">No saved widgets yet</h3>
              <p class="text-surface-500">
                Create data insights in the Data Insights page to save widgets here.
              </p>
            </div>

            <div v-else class="">
              <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 py-4">
                <div class="w-full sm:w-auto flex items-center gap-2">
                  <IconField icon-position="left" class="w-full sm:w-80">
                    <InputIcon class="pi pi-search" />
                    <InputText 
                      v-model="widgetSearchText" 
                      placeholder="Search widgets..." 
                      class="w-full"
                    />
                  </IconField>
                  <Button 
                    v-if="widgetSearchText"
                    icon="pi pi-times" 
                    severity="secondary"
                    text
                    rounded
                    @click="widgetSearchText = ''"
                    aria-label="Clear search"
                  />
                </div>
              </div>
              
              <!-- No search results -->
              <div v-if="filteredWidgets.length === 0 && widgetSearchText" class="text-center py-12">
                <div class="mb-4">
                  <i class="pi pi-search text-4xl text-surface-300"></i>
                </div>
                <h3 class="text-lg font-semibold text-surface-800 mb-2">No widgets found</h3>
                <p class="text-surface-500">
                  No widgets match your search "{{ widgetSearchText }}"
                </p>
              </div>
              
              <!-- Widgets grid -->
              <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                <SavedWidgetItem
                  v-for="widget in filteredWidgets"
                  :key="widget.id || 'unknown'"
                  :widget="widget"
                  @delete="confirmDelete"
                />
              </div>
            </div>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showDeleteDialog"
      modal
      header="Delete Widget"
      :style="{ width: '450px' }"
    >
      <div class="flex items-start gap-3">
        <i class="pi pi-exclamation-triangle text-3xl text-orange-500"></i>
        <div>
          <p class="text-surface-700">
            Are you sure you want to delete this widget? This action cannot be undone.
          </p>
        </div>
      </div>
      <template #footer>
        <Button
          label="Cancel"
          severity="secondary"
          @click="showDeleteDialog = false"
        />
        <Button
          label="Delete"
          severity="danger"
          @click="deleteWidget"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
// @ts-ignore
import { ref, defineProps, onMounted, watch, computed } from 'vue'
import { InputText, Textarea, Button, Tabs, TabList, Tab, TabPanels, TabPanel, Dialog, IconField, InputIcon } from 'primevue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { Kinotic } from '@kinotic-ai/core'
import { useToast } from 'primevue/usetoast'
import { DataInsightsWidgetEntityRepository } from '@/services/DataInsightsWidgetEntityRepository'
import type { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import SavedWidgetItem from '@/components/SavedWidgetItem.vue'
import { isDark as darkMode } from '@/composables/useTheme'

defineProps({
  applicationId: {
    type: String,
    required: true
  }
})

const toast = useToast()
const appName = ref('')
const appDescription = ref('')
const enableGraphQL = ref(false)
const enableOpenAPI = ref(false)
const enableMCP = ref(false)
const loading = ref(false)
const activeTab = ref(0)

const widgetService = new DataInsightsWidgetEntityRepository()
const savedWidgets = ref<DataInsightsWidget[]>([])
const loadingWidgets = ref(false)
const showDeleteDialog = ref(false)
const widgetToDelete = ref<string | null>(null)
const widgetSearchText = ref('')
const isDark = darkMode

watch(() => APPLICATION_STATE.currentApplication, (newApp) => {
  if (newApp) {
    appName.value = newApp.id || ''
    appDescription.value = newApp.description || ''
    enableGraphQL.value = newApp.enableGraphQL || false
    enableOpenAPI.value = newApp.enableOpenAPI || false
    enableMCP.value = (newApp as any).enableMCP || false
  }
}, { immediate: true })

onMounted(() => {
  if (APPLICATION_STATE.currentApplication) {
    const app = APPLICATION_STATE.currentApplication
    appName.value = app.id || ''
    appDescription.value = app.description || ''
    enableGraphQL.value = app.enableGraphQL || false
    enableOpenAPI.value = app.enableOpenAPI || false
    enableMCP.value = (app as any).enableMCP || false
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
      description: appDescription.value,
      enableGraphQL: enableGraphQL.value,
      enableOpenAPI: enableOpenAPI.value,
      enableMCP: enableMCP.value
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
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to save application settings',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const loadSavedWidgets = async () => {
  if (!APPLICATION_STATE.currentApplication?.id) {
    savedWidgets.value = []
    return
  }

  loadingWidgets.value = true
  try {
    const widgets = await widgetService.findByApplicationId(APPLICATION_STATE.currentApplication.id)
    savedWidgets.value = widgets
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load saved widgets',
      life: 3000
    })
  } finally {
    loadingWidgets.value = false
  }
}

const confirmDelete = (widgetId: string) => {
  widgetToDelete.value = widgetId
  showDeleteDialog.value = true
}

const deleteWidget = async () => {
  if (!widgetToDelete.value) return

  try {
    await widgetService.deleteById(widgetToDelete.value)
    savedWidgets.value = savedWidgets.value.filter(w => w.id !== widgetToDelete.value)
    
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Widget deleted successfully',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to delete widget',
      life: 3000
    })
  } finally {
    showDeleteDialog.value = false
    widgetToDelete.value = null
  }
}

watch(activeTab, (newTab) => {
  if (newTab === 1) {
    loadSavedWidgets()
  }
})

watch(() => APPLICATION_STATE.currentApplication, () => {
  if (activeTab.value === 1) {
    loadSavedWidgets()
  }
})

const filteredWidgets = computed(() => {
  if (!widgetSearchText.value) return savedWidgets.value
  
  const searchLower = widgetSearchText.value.toLowerCase()
  return savedWidgets.value.filter(widget => {
    const name = (widget as any).name?.toLowerCase() || ''
    const description = (widget as any).description?.toLowerCase() || ''
    const widgetType = (widget as any).widgetType?.toLowerCase() || ''
      
    try {
      const config = JSON.parse((widget as any).config || '{}')
      const aiTitle = config.aiTitle?.toLowerCase() || ''
      const aiSubtitle = config.aiSubtitle?.toLowerCase() || ''
      
      return name.includes(searchLower) || 
             description.includes(searchLower) || 
             widgetType.includes(searchLower) ||
             aiTitle.includes(searchLower) ||
             aiSubtitle.includes(searchLower)
    } catch {
      return name.includes(searchLower) || 
             description.includes(searchLower) || 
             widgetType.includes(searchLower)
    }
  })
})
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

.application-settings__api-card {
  overflow: hidden;
  border: 1px solid #3a3a40;
  border-radius: 0.875rem;
  background: #171717;
}

.application-settings__api-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 17px;
}

.application-settings__api-row + .application-settings__api-row {
  border-top: none;
}

.application-settings__api-row + .application-settings__api-row::before {
  content: '';
  position: absolute;
  top: 0;
  left: 17px;
  right: 17px;
  height: 1px;
  background: #3a3a40;
}

.application-settings__api-meta {
  display: flex;
  align-items: center;
  gap: 0.625rem;
}

.application-settings__api-name {
  font-size: 0.875rem;
  color: #f4f4f5;
}

.application-settings__actions {
  display: flex;
  justify-content: flex-start;
  padding-top: 1.5rem;
}

.application-settings--light .application-settings__api-card {
  border-color: #e6e7eb;
  background: #ffffff;
}

.application-settings--light .application-settings__api-row + .application-settings__api-row {
  border-top-color: transparent;
}

.application-settings--light .application-settings__api-row + .application-settings__api-row::before {
  background: #e6e7eb;
}

.application-settings--light .application-settings__api-name {
  color: #3f424d;
}

.application-settings--dark :deep(.p-tablist) {
  border-bottom: 1px solid #525252;
  background: transparent;
}

.application-settings--light :deep(.p-tablist) {
  border-bottom: 1px solid #e6e7eb;
  background: transparent;
}

.application-settings :deep(.p-tablist-tab-list) {
  background: transparent;
}

.application-settings--dark :deep(.p-tab) {
  min-height: 47px;
  padding: 14px 15px;
  color: #a3a3a3;
  background: transparent;
  border: none;
  box-shadow: none;
  font-size: 0.875rem;
  font-weight: 500;
  line-height: 1;
}

.application-settings--light :deep(.p-tab) {
  min-height: 47px;
  padding: 14px 15px;
  color: #71717a;
  background: transparent;
  border: none;
  box-shadow: none;
  font-size: 0.875rem;
  font-weight: 500;
  line-height: 1;
}

.application-settings :deep(.p-tab-active) {
  color: #ffffff;
}

.application-settings--light :deep(.p-tab-active) {
  color: #101010;
}

.application-settings :deep(.p-tablist-active-bar) {
  height: 2px;
  background: var(--p-primary-500);
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

.application-settings__switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  min-width: 70px;
  max-width: 70px;
  height: 40px;
  min-height: 40px;
  max-height: 40px;
  padding: 0;
  border: 0;
  flex: 0 0 70px;
  box-sizing: border-box;
  background: transparent;
  cursor: pointer;
}

.application-settings__switch-shell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  min-width: 70px;
  max-width: 70px;
  height: 40px;
  min-height: 40px;
  max-height: 40px;
  border-radius: 12px;
  box-sizing: border-box;
  overflow: hidden;
}

.application-settings__switch-indicator {
  position: absolute;
  inset: 4px;
  border-radius: 8px;
}

.application-settings__switch-label {
  position: relative;
  z-index: 1;
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
}

.application-settings__switch:focus-visible {
  outline: 2px solid #ec1f52;
  outline-offset: 2px;
  border-radius: 12px;
}

.application-settings--dark .application-settings__switch-shell {
  background: #262626;
}

.application-settings--dark .application-settings__switch-indicator {
  background: #0d0d0d;
}

.application-settings--dark .application-settings__switch-label {
  color: #a3a3a3;
}

.application-settings--dark .application-settings__switch--on .application-settings__switch-label {
  color: #ffffff;
}

.application-settings--light .application-settings__switch-shell {
  background: #f4f4f5;
}

.application-settings--light .application-settings__switch-indicator {
  background: #ffffff;
}

.application-settings--light .application-settings__switch-label {
  color: #71717a;
}

.application-settings--light .application-settings__switch--on .application-settings__switch-label {
  color: #101010;
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
