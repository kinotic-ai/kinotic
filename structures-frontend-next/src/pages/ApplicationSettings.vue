<template>
  <div class="application-settings p-4">
    <h1 class="text-2xl font-semibold mb-4">Application Settings</h1>

    <Tabs :value="activeTab" @update:value="(value: string | number) => activeTab = Number(value)">
      <TabList>
        <Tab :value="0">General</Tab>
        <Tab :value="1">Saved widgets</Tab>
      </TabList>
      <TabPanels class="!p-0">
        <TabPanel :value="0">
          <div v-show="activeTab === 0">
            <div class="max-w-[400px] mx-auto">
              <form @submit.prevent="saveSettings" class="flex flex-col gap-4">
                <div>
                  <div class="mb-5">
                    <label class="block text-sm text-[#101010] mb-3 font-semibold">Name</label>
                    <InputText 
                      v-model="appName" 
                      type="text" 
                      class="w-full" 
                      disabled
                    />
                  </div>
                  <div class="mb-5">
                    <label class="block text-sm text-[#101010] mb-3 font-semibold">Description</label>
                    <Textarea 
                      v-model="appDescription" 
                      class="w-full h-[100px]"
                      rows="3" 
                    />
                  </div>
                  <div>
                    <label class="block text-sm text-[#101010] mb-3 font-semibold">API configuration</label>
                    <div class="border border-[#E6E7EB] rounded-2xl w-full divide-y divide-[#E6E7EB]">
                      <div class="flex items-center justify-between p-4">
                        <div class="flex items-center gap-2">
                          <img src="@/assets/graphql.svg" />
                          <span class="text-[#3F424D] text-sm font-normal">GraphQL</span>
                        </div>
                        <ToggleButton v-model="enableGraphQL" onLabel="On" offLabel="Off" />
                      </div>
                      <div class="flex items-center justify-between p-4">
                        <div class="flex items-center gap-2">
                          <img src="@/assets/scalar.svg" />
                          <span class="text-[#3F424D] text-sm font-normal">OpenAPI</span>
                        </div>
                        <ToggleButton v-model="enableOpenAPI" onLabel="On" offLabel="Off" />
                      </div>
                      <div class="flex items-center justify-between p-4">
                        <div class="flex items-center gap-2">
                          <img src="@/assets/mcp.svg" />
                          <span class="text-[#3F424D] text-sm font-normal">MCP (Model Context Protocol)</span>
                        </div>
                        <ToggleButton v-model="enableMCP" onLabel="On" offLabel="Off" />
                      </div>
                    </div>
                  </div>
                </div>
                <div class="flex justify-end gap-2 mt-6">
                  <Button 
                    type="submit" 
                    :disabled="loading" 
                    severity="primary" 
                    label="Save Settings" 
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
import { InputText, Textarea, Button, ToggleButton, Tabs, TabList, Tab, TabPanels, TabPanel, Dialog, IconField, InputIcon } from 'primevue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { Structures } from '@mindignited/structures-api'
import { useToast } from 'primevue/usetoast'
import { DataInsightsWidgetEntityService } from '@/services/DataInsightsWidgetEntityService'
import type { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import SavedWidgetItem from '@/components/SavedWidgetItem.vue'

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

const widgetService = new DataInsightsWidgetEntityService()
const savedWidgets = ref<DataInsightsWidget[]>([])
const loadingWidgets = ref(false)
const showDeleteDialog = ref(false)
const widgetToDelete = ref<string | null>(null)
const widgetSearchText = ref('')

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

    await Structures.getApplicationService().save(updatedApplication)
    
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
