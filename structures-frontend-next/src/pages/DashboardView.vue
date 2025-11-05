<template>
  <div class="h-full flex flex-col">
    <!-- Header -->
    <div class="flex justify-between items-center p-4 bg-white border-b border-surface-200">
      <div class="flex items-center gap-4">
        <Button @click="goBack" icon="pi pi-arrow-left" class="p-button-text p-button-sm" />
        <div>
          <h1 class="text-xl font-semibold text-surface-900">{{ dashboardTitle }}</h1>
          <p class="text-sm text-surface-500">{{ dashboard?.description || 'Dashboard' }}</p>
        </div>
      </div>
      <Button @click="editDashboard" label="Edit" icon="pi pi-pencil" 
              class="bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300 rounded-md px-3 py-2 font-medium" />
    </div>

    <!-- Dashboard Content -->
    <div class="flex-1 p-4 overflow-y-auto">
      <div v-if="loading" class="flex items-center justify-center flex-1">
        <i class="pi pi-spin pi-spinner text-3xl"></i>
      </div>
      <div v-else-if="!hasWidgets" class="flex items-center justify-center flex-1">
        <div class="text-center text-surface-500">
          <i class="pi pi-chart-bar text-6xl mb-4"></i>
          <h3 class="text-lg font-semibold mb-2">No widgets yet</h3>
          <p class="text-surface-400">This dashboard doesn't have any widgets configured.</p>
          <Button @click="editDashboard" label="Add Widgets" 
                  class="bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300 rounded-md px-3 py-2 font-medium mt-4" />
        </div>
      </div>
      <div v-else class="dashboard-view-container">
        <div class="dashboard-widgets grid gap-4" :style="dashboardGridStyle">
          <div
            v-for="widgetData in dashboardWidgets"
            :key="widgetData.instanceId"
            class="dashboard-widget bg-white rounded-lg border border-surface-200 shadow-sm"
            :style="getWidgetStyle(widgetData)"
          >
            <div class="widget-header p-4 border-b border-surface-100">
              <h3 class="font-semibold text-surface-900">{{ getWidgetTitle(widgetData.widget) }}</h3>
              <p class="text-sm text-surface-500 mt-1">{{ getWidgetSubtitle(widgetData.widget) }}</p>
            </div>
            <div class="widget-content p-4">
              <div class="widget-chart-area" :data-widget-id="widgetData.widget.id">
                <div class="widget-loading-overlay absolute inset-0 bg-white bg-opacity-90 flex items-center justify-center z-10">
                  <div class="text-center">
                    <i class="pi pi-spin pi-spinner text-blue-500 text-lg mb-1"></i>
                    <div class="text-xs text-gray-600">Loading...</div>
                  </div>
                </div>
                <div class="widget-preview-content" :data-widget-id="widgetData.widget.id">
                  <div class="chart-placeholder" style="display: none; color: #999; font-size: 12px; text-align: center;">
                    Loading chart...
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { GridStack } from 'gridstack'
import 'gridstack/dist/gridstack.min.css'
import { DashboardEntityService } from '@/services/DashboardEntityService'
import { Dashboard } from '@/domain/Dashboard'
import { DataInsightsWidgetEntityService } from '@/services/DataInsightsWidgetEntityService'
import { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const toast = useToast()

const props = defineProps<{ 
  applicationId: string
  dashboardId: string 
}>()

const dashboardService = new DashboardEntityService()
const widgetService = new DataInsightsWidgetEntityService()

const dashboard = ref<Dashboard | null>(null)
const dashboardTitle = ref('')
const loading = ref(true)
const savedWidgets = ref<DataInsightsWidget[]>([])
const dashboardWidgets = ref<any[]>([])
const executedScripts = new Set<string>()
const definedElements = new Set<string>()

const hasWidgets = computed(() => dashboardWidgets.value.length > 0)

const dashboardGridStyle = computed(() => {
  // Create a CSS Grid layout based on widget positions
  return {
    display: 'grid',
    gridTemplateColumns: 'repeat(12, 1fr)',
    gap: '1rem',
    gridAutoRows: 'minmax(200px, auto)'
  }
})

const loadWidgets = async () => {
  try {
    savedWidgets.value = await widgetService.findByApplicationId(props.applicationId)
    
    setTimeout(() => {
      savedWidgets.value.forEach((widget) => {
        executeWidgetHTML(widget)
      })
    }, 500)
    
  } catch (error) {
    console.error('Error loading widgets:', error)
  }
}

const executeWidgetHTML = (widget: DataInsightsWidget, targetContainer?: HTMLElement) => {
  const config = JSON.parse(widget.config || '{}')
  const htmlContent = config.originalRawHtml || widget.src
  
  if (!htmlContent) return
  
  try {
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    const elementName = elementNameMatch ? elementNameMatch[1] : null
    
    if (!elementName) return
    
    if (widget.id && executedScripts.has(widget.id)) {
      setTimeout(() => {
        if (elementName && customElements.get(elementName)) {
          createWidgetElement(widget, elementName, targetContainer)
        }
      }, 100)
      return
    }
    if (customElements.get(elementName) || definedElements.has(elementName)) {
      if (widget.id) {
        executedScripts.add(widget.id)
      }
      setTimeout(() => {
        createWidgetElement(widget, elementName, targetContainer)
      }, 100)
      return
    }
    eval(htmlContent)
    definedElements.add(elementName)
    if (widget.id) {
      executedScripts.add(widget.id)
    }
    setTimeout(() => {
      if (elementName && customElements.get(elementName)) {
        createWidgetElement(widget, elementName, targetContainer)
      }
    }, 500)
  } catch (error) {
    console.error('Error executing widget HTML:', error)
  }
}

const createWidgetElement = (widget: DataInsightsWidget, elementName: string, targetContainer?: HTMLElement) => {
  const previewContainer = targetContainer || document.querySelector(`[data-widget-id="${widget.id}"] .widget-preview-content`)
  
  if (previewContainer) {
    const element = document.createElement(elementName)
    previewContainer.innerHTML = ''
    previewContainer.appendChild(element)
    
    setTimeout(() => {
      if (element.shadowRoot) {
        const style = document.createElement('style')
        style.textContent = `
          h1, h2, h3, h4, h5, h6, p, span, label, title, desc, .title, .description { display: none !important; } 
          :host { 
            padding: 0 !important; 
            margin: 0 !important; 
            border: none !important; 
            box-shadow: none !important; 
            background: transparent !important;
            width: 100% !important;
            height: 100% !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
          }
          canvas, svg {
            max-width: 100% !important;
            max-height: 100% !important;
            width: auto !important;
            height: auto !important;
            display: block !important;
            margin: auto !important;
          }
          .apexcharts-canvas, .apexcharts-svg, [class*="apex"] {
            margin: auto !important;
            display: block !important;
          }
          canvas, svg, [class*="chart"], [class*="apex"], [class*="visualization"], div[class*="chart"], div[class*="apex"], .chart-container {
            display: block !important;
          }
        `
        element.shadowRoot.appendChild(style)
        
        const chartElements = element.shadowRoot.querySelectorAll('canvas, svg, [class*="chart"], [class*="apex"]')
        
        if (chartElements.length === 0) {
          const placeholder = previewContainer.querySelector('.chart-placeholder') as HTMLElement
          if (placeholder) {
            placeholder.style.display = 'block'
          }
        }
      }
    }, 500)
  }
}

const loadDashboard = async () => {
  try {
    loading.value = true
    dashboard.value = await dashboardService.findById(props.dashboardId)
    dashboardTitle.value = dashboard.value?.name || 'Dashboard'
    
    if (dashboard.value?.layout) {
      try {
        const layoutData = JSON.parse(dashboard.value.layout)
        
        if (layoutData.widgets && layoutData.widgets.length > 0) {
          dashboardWidgets.value = layoutData.widgets.map((widgetData: any) => {
            const widget = savedWidgets.value.find(w => w.id === widgetData.widgetId)
            return {
              ...widgetData,
              widget: widget
            }
          }).filter((item: any) => item.widget) // Only include widgets that exist
          
          // Load widget content after a delay
          setTimeout(() => {
            dashboardWidgets.value.forEach((widgetData: any) => {
              if (widgetData.widget) {
                executeWidgetHTML(widgetData.widget)
              }
            })
          }, 1000)
        }
      } catch (error) {
        console.error('Error parsing dashboard layout:', error)
      }
    }
  } catch (error) {
    console.error('Error loading dashboard:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load dashboard',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const getWidgetStyle = (widgetData: any) => {
  const { x, y, w, h } = widgetData
  
  return {
    gridColumn: `${x + 1} / span ${w}`,
    gridRow: `${y + 1} / span ${h}`,
    minHeight: `${h * 100}px`
  }
}

const getWidgetTitle = (widget: DataInsightsWidget): string => {
  try {
    const config = JSON.parse(widget.config || '{}')
    const aiTitle = config.aiTitle
    if (aiTitle && aiTitle !== 'Test' && !aiTitle.includes('AI-generated') && aiTitle.length > 2) {
      return aiTitle
    }
    const widgetName = widget.name || ''
    if (widgetName && !widgetName.includes('AI-generated')) {
      return widgetName
    }
    
    return 'Data Insight'
  } catch {
    return 'Data Insight'
  }
}

const getWidgetSubtitle = (widget: DataInsightsWidget): string => {
  try {
    const config = JSON.parse(widget.config || '{}')
    const aiSubtitle = config.aiSubtitle
    if (aiSubtitle && !aiSubtitle.includes('AI-generated') && !aiSubtitle.includes('widget for:') && aiSubtitle.length > 5) {
      return aiSubtitle
    }
    const widgetDesc = widget.description || ''
    if (widgetDesc && !widgetDesc.includes('AI-generated')) {
      return widgetDesc
    }
    
    return 'Data visualization'
  } catch {
    return 'Data visualization'
  }
}

const goBack = () => {
  router.push(`/application/${props.applicationId}/dashboards`)
}

const editDashboard = () => {
  router.push(`/application/${props.applicationId}/dashboards/${props.dashboardId}/edit`)
}

onMounted(async () => {
  await loadWidgets()
  await loadDashboard()
})
</script>

<style scoped>
.dashboard-view-container {
  max-width: 100%;
  margin: 0 auto;
}

.dashboard-widget {
  display: flex;
  flex-direction: column;
  min-height: 200px;
  transition: box-shadow 0.2s ease;
}

.dashboard-widget:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.widget-header {
  flex-shrink: 0;
}

.widget-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.widget-chart-area {
  height: 200px;
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border-radius: 4px;
}

.widget-preview-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  min-height: 160px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.widget-preview-content :deep(canvas),
.widget-preview-content :deep(svg) {
  max-width: 100% !important;
  max-height: 100% !important;
  width: auto !important;
  height: auto !important;
  display: block !important;
  margin: auto !important;
}

.widget-preview-content :deep(.apexcharts-canvas),
.widget-preview-content :deep(.apexcharts-svg),
.widget-preview-content :deep([class*="apex"]) {
  margin: auto !important;
  display: block !important;
}

.widget-preview-content :deep(*) {
  pointer-events: none;
}
</style>
