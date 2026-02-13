<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import { createDebug } from '@/util/debug'

const debug = createDebug('saved-widget-item');

const props = defineProps<{
  widget: DataInsightsWidget
}>()

const emit = defineEmits<{
  delete: [id: string]
}>()

const previewLoaded = ref(false)

const getWidgetTitle = (): string => {
  try {
    const name = props.widget.dataInsightsComponent?.name || ''
    if (name && name.length > 0) {
      return name
    }
    return 'Data Insight'
  } catch {
    return 'Data Insight'
  }
}

const getWidgetSubtitle = (): string => {
  try {
    const description = props.widget.dataInsightsComponent?.description || ''
    if (description && description.length > 0) {
      return description
    }
    return 'Data visualization'
  } catch {
    return 'Data visualization'
  }
}

const loadEchartsIfNeeded = (): Promise<void> => {
  return new Promise((resolve) => {
    if ((window as any).echarts) {
      resolve()
      return
    }
    
    if ((window as any).echartsLoading) {
      const checkInterval = setInterval(() => {
        if ((window as any).echarts) {
          clearInterval(checkInterval)
          resolve()
        }
      }, 100)
      return
    }
    
    ;(window as any).echartsLoading = true
    const script = document.createElement('script')
    script.src = 'https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js'
    script.onload = () => {
      ;(window as any).echartsLoading = false
      resolve()
    }
    script.onerror = () => {
      ;(window as any).echartsLoading = false
      resolve()
    }
    document.head.appendChild(script)
  })
}

const executeWidgetHTML = async () => {
  const htmlContent = props.widget.dataInsightsComponent?.rawHtml || ''
  
  if (!htmlContent) return
  
  if (htmlContent.includes('echarts')) {
    await loadEchartsIfNeeded()
  }
  
  executeWidgetElement(htmlContent)
}

const executeWidgetElement = (htmlContent: string) => {
  try {
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    let elementName = elementNameMatch ? elementNameMatch[1] : null
    
    if (!elementName) return
    
    // If element already exists, create unique name for this widget instance
    if (customElements.get(elementName)) {
      debug('Element already registered, using existing: %s', elementName)
      // Check if it's the same class or different
      const existingElement = customElements.get(elementName)
      const newClassMatch = htmlContent.match(/class\s+(\w+)\s+extends\s+HTMLElement/)
      const newClassName = newClassMatch ? newClassMatch[1] : null
      
      // If different implementation, create unique element name
      if (newClassName && existingElement && existingElement.name !== newClassName) {
        const uniqueName = `${elementName}-${props.widget.id?.substring(0, 8)}`
        debug('Element already registered, creating unique name: %s', uniqueName)
        const modifiedHtml = htmlContent.replace(
          `customElements.define('${elementName}'`,
          `customElements.define('${uniqueName}'`
        )
        eval(modifiedHtml)
        elementName = uniqueName
      }
      
      createWidgetElement(elementName)
      return
    }
    
    debug('Registering new element: %s', elementName)
    eval(htmlContent)
    
    setTimeout(() => {
      if (elementName && customElements.get(elementName)) {
        createWidgetElement(elementName)
      }
    }, 500)
  } catch (error) {
    debug('Error executing widget HTML: %O', error)
  }
}

const createWidgetElement = (elementName: string) => {
  const previewContainer = document.querySelector(`[data-widget-id="${props.widget.id}"] .widget-preview-content`)
  
  debug('Creating element for: %s, registered: %s', props.widget.dataInsightsComponent?.name, !!customElements.get(elementName))
  
  if (previewContainer) {
    const element = document.createElement(elementName)
    debug('Element created: %s', elementName)
    previewContainer.innerHTML = ''
    previewContainer.appendChild(element)
    debug('Element appended to container')
    
    setTimeout(() => {
      debug('Checking shadow root - exists: %s', !!element.shadowRoot)
      
      if (element.shadowRoot) {
        debug('Shadow root confirmed')
        const style = document.createElement('style')
        element.shadowRoot.appendChild(style)
        debug('Styles appended')
        
        setTimeout(() => {
          debug('Preview loaded for: %s', props.widget.dataInsightsComponent?.name)
          previewLoaded.value = true
          
          if (element.shadowRoot) {
            const chartContainer = element.shadowRoot.querySelector('.chart-container, [id="chart"]')
            debug('Chart container in shadow: %s', !!chartContainer)
            
            const canvas = element.shadowRoot.querySelector('canvas')
            const svg = element.shadowRoot.querySelector('svg')
            debug('Canvas: %s, SVG: %s', !!canvas, !!svg)
            
            const chartElement = element.shadowRoot.querySelector('canvas, svg, [id="chart"]') as any
            if (chartElement && chartElement.__ec_inner__) {
              setTimeout(() => {
                debug('Resizing echarts')
                chartElement.__ec_inner__.resize()
                debug('ECharts resized')
              }, 500)
            } else {
              debug('No echarts instance found (might be other chart library)')
            }
          }
        }, 1000)
      } else {
        debug('No shadow root found!')
      }
    }, 300)
  } else {
    debug('Preview container not found for: %s', props.widget.id)
  }
}

onMounted(() => {
  setTimeout(() => {
    executeWidgetHTML()
  }, 200)
})
</script>

<template>
  <div
    :data-widget-id="widget.id || ''"
    class="widget-card bg-white rounded-lg border border-surface-200 overflow-hidden h-[220px]"
  >
    <div class="widget-chart-area bg-gray-50 p-2 relative">
      <div 
        v-if="!previewLoaded"
        class="widget-loading-overlay absolute inset-0 bg-white bg-opacity-90 flex items-center justify-center z-10"
      >
        <div class="text-center">
          <i class="pi pi-spin pi-spinner text-blue-500 text-lg mb-1"></i>
          <div class="text-xs text-gray-600">Loading...</div>
        </div>
      </div>
      
      <div class="widget-preview-content" :data-widget-id="widget.id">
        <div class="chart-placeholder" style="display: none; color: #999; font-size: 12px; text-align: center;">
          Loading chart...
        </div>
      </div>
    </div>
    
    <div class="p-3 relative">
      <div class="font-semibold text-sm text-gray-900 mb-1 pr-6">{{ getWidgetTitle() }}</div>
      <div class="text-xs text-gray-500 line-clamp-2 pr-6">{{ getWidgetSubtitle() }}</div>
      
      <button
        @click.stop="emit('delete', widget.id!)"
        class="absolute top-2 right-2 text-gray-400 hover:text-red-500 transition-colors p-1"
        title="Delete Widget"
      >
        <i class="pi pi-trash text-xs"></i>
      </button>
    </div>
  </div>
</template>

<style scoped>
.widget-card {
  user-select: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.widget-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.widget-chart-area {
  height: 150px;
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
}

.widget-preview-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  min-height: 100px;
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

.widget-preview-content :deep(*) {
  pointer-events: none;
}

.widget-preview-content :deep(.apexcharts-canvas),
.widget-preview-content :deep(.apexcharts-svg),
.widget-preview-content :deep([class*="apex"]) {
  margin: auto !important;
  display: block !important;
}

.widget-preview-content :deep(.chart-container) {
  height: 100% !important;
  width: 100% !important;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-clamp: 2;
}
</style>
