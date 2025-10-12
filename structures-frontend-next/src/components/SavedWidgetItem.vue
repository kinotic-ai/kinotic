<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { DataInsightsWidget } from '@/domain/DataInsightsWidget'

const props = defineProps<{
  widget: DataInsightsWidget
}>()

const emit = defineEmits<{
  delete: [id: string]
}>()

const previewLoaded = ref(false)

const getWidgetTitle = (): string => {
  try {
    const config = JSON.parse(props.widget.config || '{}')
    const aiTitle = config.aiTitle
    if (aiTitle && aiTitle !== 'Test' && !aiTitle.includes('AI-generated') && aiTitle.length > 2) {
      return aiTitle
    }
    const widgetName = props.widget.name || ''
    if (widgetName && !widgetName.includes('AI-generated')) {
      return widgetName
    }
    return 'Data Insight'
  } catch {
    return 'Data Insight'
  }
}

const getWidgetSubtitle = (): string => {
  try {
    const config = JSON.parse(props.widget.config || '{}')
    const aiSubtitle = config.aiSubtitle
    if (aiSubtitle && !aiSubtitle.includes('AI-generated') && !aiSubtitle.includes('widget for:') && aiSubtitle.length > 5) {
      return aiSubtitle
    }
    const widgetDesc = props.widget.description || ''
    if (widgetDesc && !widgetDesc.includes('AI-generated')) {
      return widgetDesc
    }
    return 'Data visualization'
  } catch {
    return 'Data visualization'
  }
}

const executeWidgetHTML = () => {
  const config = JSON.parse(props.widget.config || '{}')
  const htmlContent = config.originalRawHtml || props.widget.src
  
  if (!htmlContent) return
  
  try {
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    const elementName = elementNameMatch ? elementNameMatch[1] : null
    
    if (!elementName) return
    
    if (customElements.get(elementName)) {
      createWidgetElement(elementName)
      return
    }
    
    eval(htmlContent)
    
    setTimeout(() => {
      if (elementName && customElements.get(elementName)) {
        createWidgetElement(elementName)
      }
    }, 500)
  } catch (error) {
    console.error('Error executing widget HTML:', error)
  }
}

const createWidgetElement = (elementName: string) => {
  const previewContainer = document.querySelector(`[data-widget-id="${props.widget.id}"] .widget-preview-content`)
  
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
        `
        element.shadowRoot.appendChild(style)
        
        setTimeout(() => {
          previewLoaded.value = true
        }, 500)
      }
    }, 100)
  }
}

onMounted(() => {
  setTimeout(() => {
    executeWidgetHTML()
  }, 100)
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
      <div class="font-semibold text-sm text-gray-900 mb-1">{{ getWidgetTitle() }}</div>
      <div class="text-xs text-gray-500 truncate">{{ getWidgetSubtitle() }}</div>
      
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
</style>

