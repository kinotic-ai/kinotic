<template>
  <div class="saved-widgets-page p-4">
    <h1 class="text-2xl font-semibold mb-4">Saved Widgets</h1>

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

    <!-- Widgets grid -->
    <div v-else class="">
      <div class="mb-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
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
import { ref, onMounted, computed } from 'vue'
import { InputText, Button, Dialog, IconField, InputIcon } from 'primevue'
import { useToast } from 'primevue/usetoast'
import { DataInsightsWidgetEntityService } from '@/services/DataInsightsWidgetEntityService'
import type { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import SavedWidgetItem from '@/components/SavedWidgetItem.vue'
import { createDebug } from '@/util/debug'

const debug = createDebug('saved-widgets');

const props = defineProps<{
  applicationId: string
}>()

const toast = useToast()

// Saved widgets state
const widgetService = new DataInsightsWidgetEntityService()
const savedWidgets = ref<DataInsightsWidget[]>([])
const loadingWidgets = ref(false)
const showDeleteDialog = ref(false)
const widgetToDelete = ref<string | null>(null)
const widgetSearchText = ref('')

// Load saved widgets for the current application
const loadSavedWidgets = async () => {
  if (!props.applicationId) {
    savedWidgets.value = []
    return
  }

  loadingWidgets.value = true
  try {
    const widgets = await widgetService.findByApplicationId(props.applicationId)
    savedWidgets.value = widgets
  } catch (error) {
    debug('Failed to load saved widgets: %O', error)
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

// Handle widget deletion
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
    debug('Failed to delete widget: %O', error)
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

// Computed property for filtered widgets based on search
const filteredWidgets = computed(() => {
  if (!widgetSearchText.value) return savedWidgets.value
  
  const searchLower = widgetSearchText.value.toLowerCase()
  return savedWidgets.value.filter(widget => {
    const name = widget.dataInsightsComponent?.name?.toLowerCase() || ''
    const description = widget.dataInsightsComponent?.description?.toLowerCase() || ''
    
    return name.includes(searchLower) || description.includes(searchLower)
  })
})

onMounted(() => {
  loadSavedWidgets()
})
</script>

