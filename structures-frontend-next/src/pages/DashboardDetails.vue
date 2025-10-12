<script lang="ts" setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { GridStack } from 'gridstack'
import 'gridstack/dist/gridstack.min.css'
import { DashboardEntityService } from '@/services/DashboardEntityService'
import { Dashboard } from '@/domain/Dashboard'
import { DataInsightsWidgetEntityService } from '@/services/DataInsightsWidgetEntityService'
import { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Calendar from 'primevue/calendar'
import { useToast } from 'primevue/usetoast'
import SavedWidgetItem from '@/components/SavedWidgetItem.vue'
import { DateRangeObservable } from '../observables/DateRangeObservable'

const router = useRouter()
const toast = useToast()

const props = defineProps<{ 
  applicationId: string
  dashboardId: string
  mode?: 'view' | 'edit'
}>()  

const dashboardService = new DashboardEntityService()
const widgetService = new DataInsightsWidgetEntityService()

const dashboard = ref<Dashboard | null>(null)
const dashboardTitle = ref('')
const loading = ref(true)
const gridStack = ref<GridStack | null>(null)
const savedWidgets = ref<DataInsightsWidget[]>([])
const widgetSearchText = ref('')
const addedWidgetIds = new Set<string>()
const hasWidgets = ref(false)
const loadingSidebarWidgets = ref(true)

const dateRange = ref<{ startDate: Date | null, endDate: Date | null }>({
  startDate: null,
  endDate: null
})
const showDateRangePicker = ref(false)

const isEditMode = computed(() => {
  if (props.mode) {
    return props.mode === 'edit'
  }
  return props.dashboardId === 'new'
})


const filteredWidgets = computed(() => {
  if (!widgetSearchText.value) return savedWidgets.value
  return savedWidgets.value.filter(w =>
    w.name.toLowerCase().includes(widgetSearchText.value.toLowerCase())
  )
})

const hasWidgetsInGrid = computed(() => {
  return hasWidgets.value
})

const isNewDashboard = computed(() => {
  return props.dashboardId === 'new' || !dashboard.value?.id
})

const saveButtonLabel = computed(() => {
  return isNewDashboard.value ? 'Create' : 'Save'
})

const headerTitle = computed(() => {
  return dashboard.value?.name || dashboardTitle.value || 'Dashboard'
})

const headerSubtitle = computed(() => {
  return dashboard.value?.description || 'Dashboard'
})

const loadWidgets = async () => {
  try {
    loadingSidebarWidgets.value = true
    savedWidgets.value = await widgetService.findByApplicationId(props.applicationId)
    loadingSidebarWidgets.value = false
  } catch (error) {
    loadingSidebarWidgets.value = false
  }
}


const loadDashboard = async () => {
  try {
    loading.value = true
    if (props.dashboardId === 'new') {
      dashboard.value = new Dashboard()
      dashboard.value.applicationId = props.applicationId
      dashboard.value.name = ''
      dashboard.value.description = 'New dashboard'
      dashboard.value.layout = ''
      dashboardTitle.value = ''
      hasWidgets.value = false
      loading.value = false
      return
    }
    
    dashboard.value = await dashboardService.findById(props.dashboardId)
    dashboardTitle.value = dashboard.value?.name || 'Dashboard'
    
    if (dashboard.value?.layout) {
      try {
        const layoutData = JSON.parse(dashboard.value.layout)
        
        if (layoutData.widgets && layoutData.widgets.length > 0) {
          hasWidgets.value = true
          setTimeout(() => {
            
            layoutData.widgets.forEach((widgetData: any) => {
              const widget = savedWidgets.value.find(w => w.id === widgetData.widgetId)
              
              if (widget) {
                addWidgetToGrid(widget, widgetData.x, widgetData.y, widgetData.w, widgetData.h, widgetData.instanceId)
              } else {
              }
            })
            setTimeout(() => {
              addResizeIcons()
            }, 2000)
          }, 1500)
        } else {
        }
      } catch (error) {
      }
    } else {
    }
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const addResizeIcons = () => {
  const resizeHandles = document.querySelectorAll('.grid-stack-item .ui-resizable-se')
  
  if (resizeHandles.length === 0) {
  }
  
  resizeHandles.forEach((handle) => {
    
    handle.innerHTML = ''
    
    const icon = document.createElement('i')
    icon.className = 'pi pi-arrow-up-right-and-arrow-down-left-from-center'
    icon.style.fontSize = '12px'
    icon.style.color = '#6b7280'
    
    handle.appendChild(icon)
    
  })
}

const setupResizeIconObserver = () => {
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === Node.ELEMENT_NODE) {
            const element = node as Element
            if (element.classList.contains('grid-stack-item') || element.querySelector('.grid-stack-item')) {
              setTimeout(() => {
                addResizeIcons()
              }, 100)
            }
          }
        })
      }
    })
  })
  
  const gridContainer = document.querySelector('.grid-stack')
  if (gridContainer) {
    observer.observe(gridContainer, { childList: true, subtree: true })
  }
}

const initGrid = () => {
  const gridOptions = {
    cellHeight: 80,
    column: 12,
    margin: 5,
    float: true,
    acceptWidgets: true,
    resizable: { handles: 'se' },
    draggable: { handle: '.grid-stack-item-content' }
  }
  
  // Disable interactions in view mode
  if (!isEditMode.value) {
    gridOptions.acceptWidgets = false
    gridOptions.resizable = { handles: '' }
    gridOptions.draggable = { handle: '' }
  }
  
  gridStack.value = GridStack.init(gridOptions)
  
  // Apply view mode settings immediately after initialization
  if (!isEditMode.value) {
    updateGridMode()
  }
  
  // In view mode, immediately disable all interactions
  if (!isEditMode.value) {
    const hideDeleteButtons = () => {
      // Remove all resize handles, drag handles, and delete buttons
      const gridItems = document.querySelectorAll('.grid-stack-item')
      gridItems.forEach((item) => {
        const resizeHandles = item.querySelectorAll('.ui-resizable-handle, .ui-resizable-se, .grid-stack-item-resize')
        resizeHandles.forEach((handle) => {
          const handleEl = handle as HTMLElement
          handleEl.style.display = 'none'
          handleEl.style.visibility = 'hidden'
        })
        
        // Remove drag cursor but enable scrolling
        const content = item.querySelector('.grid-stack-item-content')
        if (content) {
          const contentEl = content as HTMLElement
          contentEl.style.cursor = 'default'
          contentEl.style.overflow = 'auto'
          contentEl.style.height = '100%'
        }
        
        // Enable scrolling for the widget but lock it in place
        const itemEl = item as HTMLElement
        itemEl.style.overflow = 'auto'
        
        // Completely lock widget in place
        itemEl.style.position = 'absolute'
        itemEl.style.left = itemEl.style.left || '0px'
        itemEl.style.top = itemEl.style.top || '0px'
        itemEl.setAttribute('data-gs-locked', 'true')
        itemEl.setAttribute('data-gs-no-move', 'true')
        itemEl.setAttribute('data-gs-no-resize', 'true')
        
        // Hide delete buttons - try multiple selectors more aggressively
        const deleteBtns = item.querySelectorAll('.remove-btn, button.remove-btn, .grid-stack-item-remove, .pi-trash, .pi-times, [class*="remove"], [class*="delete"], [class*="close"], button[class*="trash"], i[class*="trash"], i[class*="times"], .delete-button, .close-button, .remove-button')
        deleteBtns.forEach((btn) => {
          const btnEl = btn as HTMLElement
          btnEl.style.display = 'none'
          btnEl.style.visibility = 'hidden'
          btnEl.style.opacity = '0'
          btnEl.style.pointerEvents = 'none'
        })
      })
    }
    
    // Run multiple times to catch dynamically added elements
    setTimeout(hideDeleteButtons, 100)
    setTimeout(hideDeleteButtons, 500)
    setTimeout(hideDeleteButtons, 1000)
  } else {
    // In edit mode, ensure delete buttons and resize handles are visible
    const showEditControls = () => {
      const gridItems = document.querySelectorAll('.grid-stack-item')
      gridItems.forEach((item) => {
        // Show delete buttons
        const deleteBtns = item.querySelectorAll('.remove-btn, button.remove-btn, .grid-stack-item-remove, .pi-trash, .pi-times, [class*="remove"], [class*="delete"], [class*="close"], button[class*="trash"], i[class*="trash"], i[class*="times"], .delete-button, .close-button, .remove-button')
        deleteBtns.forEach((btn) => {
          const btnEl = btn as HTMLElement
          btnEl.style.display = 'block'
          btnEl.style.visibility = 'visible'
          btnEl.style.opacity = '1'
          btnEl.style.pointerEvents = 'auto'
        })
        
        // Ensure content is interactive
        const content = item.querySelector('.grid-stack-item-content')
        if (content) {
          const contentEl = content as HTMLElement
          contentEl.style.cursor = 'move'
          contentEl.style.pointerEvents = 'auto'
        }
      })
    }
    
    // Run to ensure edit controls are visible
    setTimeout(showEditControls, 100)
    setTimeout(showEditControls, 500)
  }
  
  setTimeout(() => {
    addResizeIcons()
  }, 500)
  
  setupResizeIconObserver()
}

const setupDragDrop = () => {
  const widgets = document.querySelectorAll('.widget-card')
  
  widgets.forEach(card => {
    (card as HTMLElement).draggable = true
    
    card.addEventListener('dragstart', (e: Event) => {
      const dragEvent = e as DragEvent
      const widgetId = card.getAttribute('data-widget-id')
      if (dragEvent.dataTransfer && widgetId) {
        dragEvent.dataTransfer.setData('text/plain', widgetId)
        
        const dragImage = card.cloneNode(true) as HTMLElement
        dragImage.style.position = 'absolute'
        dragImage.style.top = '-1000px'
        dragImage.style.left = '-1000px'
        dragImage.style.width = '300px'
        dragImage.style.height = '200px'
        dragImage.style.transform = 'rotate(5deg)'
        dragImage.style.opacity = '0.8'
        dragImage.style.border = '2px solid #3b82f6'
        dragImage.style.borderRadius = '8px'
        dragImage.style.boxShadow = '0 10px 25px rgba(0, 0, 0, 0.2)'
        dragImage.style.zIndex = '1000'
        
        document.body.appendChild(dragImage)
        
        dragEvent.dataTransfer.setDragImage(dragImage, 150, 100)
        
        setTimeout(() => {
          if (document.body.contains(dragImage)) {
            document.body.removeChild(dragImage)
          }
        }, 100)
      }
    })
  })

  const gridEl = document.querySelector('.grid-stack')
  if (!gridEl) return

  gridEl.addEventListener('dragover', (e: Event) => {
    e.preventDefault()
    gridEl.classList.add('drag-over')
  })

  gridEl.addEventListener('dragleave', () => {
    gridEl.classList.remove('drag-over')
  })

  gridEl.addEventListener('drop', (e: Event) => {
    e.preventDefault()
    gridEl.classList.remove('drag-over')
    
    const dragEvent = e as DragEvent
    const widgetId = dragEvent.dataTransfer?.getData('text/plain')
    if (!widgetId) return

    const widget = savedWidgets.value.find(w => w.id === widgetId)
    if (widget) {
      const gridRect = gridEl.getBoundingClientRect()
      const x = dragEvent.clientX - gridRect.left
      const y = dragEvent.clientY - gridRect.top
      
      const cellHeight = 80 + 5
      const cellWidth = gridRect.width / 12
      const col = Math.max(0, Math.min(Math.floor(x / cellWidth), 11))
      const row = Math.max(0, Math.floor(y / cellHeight))
      
      
      addWidgetToGrid(widget, col, row)
    }
  })
}

const addWidgetToGrid = (widget: DataInsightsWidget, x?: number, y?: number, w?: number, h?: number, instanceId?: string) => {
  if (!gridStack.value || !widget.id) return
  const widgetInstanceId = instanceId || `${widget.id}-${Date.now()}`

  const config = JSON.parse(widget.config || '{}')
  const htmlContent = config.originalRawHtml || widget.src

  if (htmlContent && htmlContent.includes('customElements.define')) {
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    const elementName = elementNameMatch ? elementNameMatch[1] : null
    
    if (elementName && !customElements.get(elementName)) {
      try {
        eval(htmlContent)
      } catch (error) {
      }
    }
  }

  const el = document.createElement('div')
  el.className = 'grid-stack-item'
  el.setAttribute('data-widget-id', widget.id)
  el.setAttribute('data-instance-id', widgetInstanceId)
  el.innerHTML = `
    <div class="grid-stack-item-content bg-white rounded-lg border border-surface-200 h-full flex flex-col p-4">
      <!-- Title and Subtitle (Top) -->
      
      <!-- Chart/Diagram Area (Bottom) -->
      <div class="widget-chart-area bg-white p-2 flex-1">
        <div class="widget-body h-full" data-instance-id="${instanceId}"></div>
      </div>
      
      <!-- Remove Button -->
      <button class="remove-btn absolute top-2 right-2 text-gray-400 hover:text-red-500 text-sm">
        <i class="pi pi-trash"></i>
      </button>
    </div>
  `

  const removeBtn = el.querySelector('.remove-btn')
  if (removeBtn) {
    removeBtn.addEventListener('click', async () => {
      gridStack.value?.removeWidget(el)
      addedWidgetIds.delete(widgetInstanceId)
      
      setTimeout(() => {
        const remainingWidgets = document.querySelectorAll('.grid-stack-item')
        hasWidgets.value = remainingWidgets.length > 0
      }, 100)
    })
  }
  const getWidgetSize = (widget: DataInsightsWidget) => {
    const config = JSON.parse(widget.config || '{}')
    const query = config.query || ''
    
    if (query.toLowerCase().includes('pie') || query.toLowerCase().includes('donut')) {
      return { w: 3, h: 3 }
    } else if (query.toLowerCase().includes('bar') || query.toLowerCase().includes('column')) {
      return { w: 4, h: 3 }
    } else if (query.toLowerCase().includes('line') || query.toLowerCase().includes('trend')) {
      return { w: 5, h: 3 }
    } else if (query.toLowerCase().includes('scatter') || query.toLowerCase().includes('heatmap')) {
      return { w: 4, h: 4 }
    } else {
      return { w: 4, h: 3 }
    }
  }
  
  const defaultSize = getWidgetSize(widget)
  const options: any = { 
    w: w || defaultSize.w, 
    h: h || defaultSize.h,
    minW: 1,
    maxW: 12,
    minH: 1,
    maxH: 12
  }
  if (x !== undefined) options.x = Math.max(0, Math.min(x, 11))
  if (y !== undefined) options.y = Math.max(0, y)
  
  gridStack.value.makeWidget(el, options)
  addedWidgetIds.add(widgetInstanceId)
  hasWidgets.value = true
  
  const resizeObserver = new ResizeObserver((entries) => {
    entries.forEach((entry) => {
      const widgetBody = entry.target.querySelector('.widget-body')
      if (widgetBody) {
        setTimeout(() => {
          const widgetId = entry.target.getAttribute('data-widget-id')
          if (widgetId) {
            const widget = savedWidgets.value.find(w => w.id === widgetId)
            if (widget) {
              
              const config = JSON.parse(widget.config || '{}')
              const htmlContent = config.originalRawHtml || widget.src
              
              if (htmlContent && htmlContent.includes('customElements.define')) {
                const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
                const elementName = elementNameMatch ? elementNameMatch[1] : null
                
                if (elementName && customElements.get(elementName)) {
                  widgetBody.innerHTML = ''
                  const element = document.createElement(elementName)
                  widgetBody.appendChild(element)
                  
                  setTimeout(() => {
                    if (element.shadowRoot) {
                      const style = document.createElement('style')
                      style.textContent = 'h1, h2, h3, h4, h5, h6, p, span, label, title, desc, .title, .description :host { padding: 0 !important; margin: 0 !important; border: none !important; box-shadow: none !important; } canvas, svg, [class*="chart"], [class*="apex"], [class*="visualization"], div[class*="chart"], div[class*="apex"], .chart-container { display: block !important; }'
                      element.shadowRoot.appendChild(style)
                      setTimeout(() => {
                        const chartElement = element.shadowRoot.querySelector('canvas, svg, [class*="chart"], [class*="apex"]') as any
                        if (chartElement) {
                          if (chartElement.__apexcharts) {
                            chartElement.__apexcharts.resize()
                          }
                          if (typeof chartElement.resize === 'function') {
                            chartElement.resize()
                          }
                        }
                      }, 100)
                      
                    }
                  }, 100)
                }
              }
            }
          }
        }, 100)
      }
    })
  })
  
  resizeObserver.observe(el)
  
  setTimeout(() => {
    addResizeIcons()
  }, 300)
  
  setTimeout(() => {
    const elementNameMatch = htmlContent?.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    if (elementNameMatch) {
      const elementName = elementNameMatch[1]
      
      if (customElements.get(elementName)) {
        const widgetBody = el.querySelector('.widget-body')
        if (widgetBody) {
          const element = document.createElement(elementName)
          widgetBody.innerHTML = ''
          widgetBody.appendChild(element)
          
          setTimeout(() => {
            if (element.shadowRoot) {
              const h3Element = element.shadowRoot.querySelector('h3')
              const pElement = element.shadowRoot.querySelector('p')
              
              let extractedTitle = ''
              let extractedSubtitle = ''
              
              if (h3Element) {
                extractedTitle = h3Element.textContent?.trim() || ''
              }
              if (pElement) {
                extractedSubtitle = pElement.textContent?.trim() || ''
              }
              
                if (extractedTitle || extractedSubtitle) {
                  try {
                    updateWidgetConfig(widget, { 
                      aiTitle: extractedTitle, 
                      aiSubtitle: extractedSubtitle 
                    })
                } catch (error) {
                }
              }
              
              const style = document.createElement('style')
              style.textContent = 'h1, h2, h3, h4, h5, h6, p, span, label, title, desc, .title, .description :host { padding: 0 !important; margin: 0 !important; border: none !important; box-shadow: none !important; } canvas, svg, [class*="chart"], [class*="apex"], [class*="visualization"], div[class*="chart"], div[class*="apex"], .chart-container { display: block !important; }'
              element.shadowRoot.appendChild(style)
            }
          }, 100)
        }
      }
    }
  }, 1000)
}

const createDashboard = async () => {
  if (!dashboardTitle.value || dashboardTitle.value.trim() === '') {
    toast.add({ severity: 'warn', summary: 'Warning', detail: 'Please enter a dashboard title', life: 3000 })
    return
  }
  
  try {
    const newDashboard: any = {
      id: null,
      name: dashboardTitle.value.trim(),
      description: 'Dashboard',
      applicationId: props.applicationId,
      layout: JSON.stringify({ widgets: [] }),
      created: new Date(),
      updated: new Date()
    }
    
    
    const savedDashboard = await dashboardService.save(newDashboard)
    
    toast.add({ severity: 'success', summary: 'Created', detail: `Dashboard "${savedDashboard.name}" created successfully`, life: 3000 })
    
    // Redirect to view mode of the created dashboard
    setTimeout(() => {
      router.push(`/application/${props.applicationId}/dashboards/${savedDashboard.id}`)
    }, 500)
  } catch (error: any) {
    toast.add({ 
      severity: 'error', 
      summary: 'Error', 
      detail: error?.message || 'Failed to create dashboard',
      life: 3000
    })
  }
}

const updateDashboard = async () => {
  if (!dashboard.value || !gridStack.value) return
  
  try {
    const gridItems = document.querySelectorAll('.grid-stack .grid-stack-item')
    const widgetInstances: any[] = []
    gridItems.forEach(item => {
      const el = item as HTMLElement
      const instanceId = el.getAttribute('data-instance-id')
      const widgetId = el.getAttribute('data-widget-id')
      const x = parseInt(el.getAttribute('gs-x') || '0')
      const y = parseInt(el.getAttribute('gs-y') || '0')
      const w = parseInt(el.getAttribute('gs-w') || '4')
      const h = parseInt(el.getAttribute('gs-h') || '3')
      
      if (widgetId && instanceId) {
        widgetInstances.push({ instanceId, widgetId, x, y, w, h })
      }
    })
    
    dashboard.value.name = dashboardTitle.value
    dashboard.value.layout = JSON.stringify({ widgets: widgetInstances })
    dashboard.value.updated = new Date()
    
    await dashboardService.save(dashboard.value)
    
    toast.add({ severity: 'success', summary: 'Updated', detail: 'Dashboard updated successfully', life: 3000 })
    
    // Redirect to view mode after update
    setTimeout(() => {
      router.push(`/application/${props.applicationId}/dashboards/${props.dashboardId}`)
    }, 500)
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to update dashboard', life: 3000 })
  }
}

const saveDashboard = async () => {
  if (isNewDashboard.value) {
    await createDashboard()
  } else {
    await updateDashboard()
  }
}

const goBack = () => {
  router.push(`/application/${props.applicationId}/dashboards`)
}

const updateDateRange = () => {
  if (typeof window !== 'undefined' && window.globalDateRangeObservable) {
    window.globalDateRangeObservable.updateDateRange(dateRange.value)
  }
}

const toggleDateRangePicker = () => {
  showDateRangePicker.value = !showDateRangePicker.value
}

const enterEditMode = () => {
  router.push(`/application/${props.applicationId}/dashboards/${props.dashboardId}/edit`)
}

const exitEditMode = () => {
  router.push(`/application/${props.applicationId}/dashboards/${props.dashboardId}`)
}

const updateGridMode = () => {
  if (!gridStack.value) return
  
  if (!isEditMode.value) {
    gridStack.value.setStatic(true)
    gridStack.value.enableMove(false)
    gridStack.value.enableResize(false)
  } else {
    gridStack.value.setStatic(false)
    gridStack.value.enableMove(true)
    gridStack.value.enableResize(true)
  }
}

const updateWidgetConfig = (widget: DataInsightsWidget, updates: { aiTitle?: string, aiSubtitle?: string }) => {
  try {
    const config = JSON.parse(widget.config || '{}')
    let configUpdated = false
    
    if (updates.aiTitle && !config.aiTitle) {
      config.aiTitle = updates.aiTitle
      configUpdated = true
    }
    if (updates.aiSubtitle && !config.aiSubtitle) {
      config.aiSubtitle = updates.aiSubtitle
      configUpdated = true
    }
    
    if (configUpdated) {
      widget.config = JSON.stringify(config)
      return true
    }
    return false
  } catch (error) {
    return false
  }
}


watch(isEditMode, () => {
  if (gridStack.value) {
    updateGridMode()
    
    if (isEditMode.value) {
      setTimeout(() => setupDragDrop(), 100)
    }
  }
})

onMounted(async () => {
  if (!window.globalDateRangeObservable) {
    window.globalDateRangeObservable = new DateRangeObservable()
  }
  
  await Promise.all([loadDashboard(), loadWidgets()])
  
  setTimeout(() => {
    initGrid()
    
    if (isEditMode.value) {
      setTimeout(() => setupDragDrop(), 100)
    }
  }, 200)
})
</script>

<template>
  <div class="h-full flex">
    <div class="flex-1 flex flex-col">
      <div class="flex justify-between items-center p-4 bg-white border-b border-surface-200">
        <div class="flex items-center gap-4">
          <Button @click="goBack" icon="pi pi-arrow-left" class="p-button-text p-button-sm" />
          <!-- View Mode: Display title and description -->
          <div v-if="!isEditMode">
            <h1 class="text-xl font-semibold text-surface-900">{{ headerTitle }}</h1>
            <p class="text-sm text-surface-500">{{ headerSubtitle }}</p>
          </div>
          <!-- Edit Mode: Editable title input -->
          <InputText v-if="isEditMode" v-model="dashboardTitle" placeholder="Dashboard Title" class="text-xl font-semibold" />
        </div>
        <div class="flex gap-2 items-center">
          <!-- View Mode: Date Range Picker -->
          <div v-if="!isEditMode" class="flex items-center gap-2">
            <Button
              @click="toggleDateRangePicker"
              :class="showDateRangePicker ? 'p-button-primary' : 'p-button-outlined'"
              icon="pi pi-calendar"
              size="small"
              :label="showDateRangePicker ? 'Hide Date Range' : 'Set Date Range'"
            />
            
            <div v-if="showDateRangePicker" class="flex items-center gap-2 bg-white p-2 rounded border">
              <div class="flex items-center gap-2">
                <label class="text-sm font-medium text-surface-700">From:</label>
                <Calendar
                  v-model="dateRange.startDate"
                  @date-select="updateDateRange"
                  placeholder="Start Date"
                  size="small"
                  showIcon
                />
              </div>
              
              <div class="flex items-center gap-2">
                <label class="text-sm font-medium text-surface-700">To:</label>
                <Calendar
                  v-model="dateRange.endDate"
                  @date-select="updateDateRange"
                  placeholder="End Date"
                  size="small"
                  showIcon
                />
              </div>
              
              <Button
                @click="dateRange = { startDate: null, endDate: null }; updateDateRange()"
                icon="pi pi-times"
                size="small"
                class="p-button-text"
                title="Clear date range"
              />
            </div>
          </div>
          
          <!-- View Mode: Edit button -->
          <Button v-if="!isEditMode" @click="enterEditMode" label="Edit" icon="pi pi-pencil" class="p-button-primary" />
          <!-- Edit Mode: Cancel and Save buttons -->
          <template v-if="isEditMode">
            <Button @click="exitEditMode" label="Cancel" class="p-button-outlined" />
            <Button @click="saveDashboard" :label="saveButtonLabel" class="p-button-primary" />
          </template>
        </div>
      </div>

       <div class="flex-1 p-4 flex flex-col">
         <div v-if="loading" class="flex items-center justify-center flex-1">
           <i class="pi pi-spin pi-spinner text-3xl"></i>
         </div>
         <div v-else :class="['grid-stack flex-1 overflow-y-auto relative', { 'view-mode': !isEditMode, 'edit-mode': isEditMode }]">
           <div v-if="!hasWidgetsInGrid && isEditMode" class="absolute inset-0 flex items-center justify-center pointer-events-none z-10">
             <div class="text-center text-gray-500 bg-white bg-opacity-90 p-6 rounded-lg">
               <p class="text-sm text-gray-500">Drag and drop widgets from the sidebar to start building your dashboard</p>
             </div>
           </div>
           <div v-if="!hasWidgetsInGrid && !isEditMode" class="flex items-center justify-center flex-1">
             <div class="text-center text-surface-500">
               <i class="pi pi-chart-bar text-6xl mb-4"></i>
               <h3 class="text-lg font-semibold mb-2">No widgets yet</h3>
               <p class="text-surface-400 mb-4">This dashboard doesn't have any widgets configured.</p>
               <Button @click="enterEditMode" label="Add Widgets" class="p-button-primary" />
             </div>
           </div>
         </div>
       </div>
    </div>

    <!-- Sidebar: Only visible in edit mode -->
    <div v-if="isEditMode" class="w-80 border-l border-surface-200 flex flex-col h-full overflow-y-auto">
      <div class="p-4 bg-white flex-shrink-0">
        <h3 class="text-lg font-semibold mb-3">Widgets</h3>
        <InputText v-model="widgetSearchText" placeholder="Search..." class="w-full mb-3" />
      </div>
      <div class="flex-1 p-4 overflow-y-auto">
        <div v-if="loadingSidebarWidgets" class="flex items-center justify-center h-32">
          <div class="text-center">
            <i class="pi pi-spin pi-spinner text-blue-500 text-2xl mb-2"></i>
            <div class="text-sm text-gray-600">Loading widgets...</div>
          </div>
        </div>
        <div v-else class="space-y-2 sidebar-widgets">
          <div
            v-for="widget in filteredWidgets"
            :key="widget.id || ''"
            class="cursor-move"
          >
            <SavedWidgetItem
              :widget="widget"
              @delete="() => {}"
            />
          </div>
        </div>
      </div>
      </div>
    </div>
</template>

<style scoped>
.grid-stack {
  min-height: 400px;
  height: 100%;
  min-height: calc(100vh - 200px);
  transition: background-color 0.2s ease;
  position: relative;
}

.grid-stack:not(.drag-over) {
  min-height: calc(100vh - 200px);
}

.grid-stack.drag-over {
  background-color: rgba(59, 130, 246, 0.05);
  border: 2px dashed rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.grid-stack.drag-over::before {
  content: 'Drop widget here';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  pointer-events: none;
  z-index: 1;
}

:deep(.grid-stack-item-content) {
  cursor: move;
}

/* View mode styles - widgets are completely locked in place */
.view-mode .grid-stack-item {
  overflow: auto !important;
  position: absolute !important;
  cursor: default !important;
  user-select: text !important;
}

.view-mode .grid-stack-item-content {
  cursor: default !important;
  overflow: auto !important;
  height: 100% !important;
  user-select: text !important;
}

/* Completely disable any movement in view mode */
.view-mode .grid-stack-item[data-gs-locked="true"],
.view-mode .grid-stack-item[data-gs-no-move="true"] {
  position: absolute !important;
  transform: none !important;
  left: unset !important;
  top: unset !important;
  right: unset !important;
  bottom: unset !important;
}

/* Hide delete button in view mode with maximum specificity */
.view-mode .grid-stack-item .remove-btn,
.view-mode .grid-stack-item button.remove-btn,
.view-mode .remove-btn,
.view-mode .grid-stack-item .grid-stack-item-remove,
.view-mode .grid-stack-item .ui-dialog-titlebar-close,
.view-mode .grid-stack-item .pi-trash,
.view-mode .grid-stack-item .pi-times,
.view-mode .grid-stack-item [class*="remove"],
.view-mode .grid-stack-item [class*="delete"],
.view-mode .grid-stack-item [class*="close"],
.view-mode .grid-stack-item button[class*="trash"],
.view-mode .grid-stack-item i[class*="trash"],
.view-mode .grid-stack-item i[class*="times"],
.view-mode .grid-stack-item .delete-button,
.view-mode .grid-stack-item .close-button,
.view-mode .grid-stack-item .remove-button {
  display: none !important;
  visibility: hidden !important;
  opacity: 0 !important;
  pointer-events: none !important;
}

/* Hide all resize handles in view mode */
.view-mode .grid-stack-item .ui-resizable-se,
.view-mode .grid-stack-item .ui-resizable-handle,
.view-mode .grid-stack-item .grid-stack-item-resize,
.view-mode .ui-resizable-se,
.view-mode .ui-resizable-handle,
.view-mode .grid-stack-item-resize {
  display: none !important;
  visibility: hidden !important;
}

/* Completely hide all resize handles and drag handles in view mode */
.view-mode .grid-stack-item::after,
.view-mode .grid-stack-item::before {
  display: none !important;
}

/* Disable dragging in view mode but allow scrolling */

/* Allow pointer events for scrollable content and charts in view mode */
.view-mode .grid-stack-item .widget-body,
.view-mode .grid-stack-item .widget-preview-content,
.view-mode .grid-stack-item canvas,
.view-mode .grid-stack-item svg,
.view-mode .grid-stack-item [class*="chart"],
.view-mode .grid-stack-item [class*="apex"],
.view-mode .grid-stack-item .widget-content,
.view-mode .grid-stack-item .content,
.view-mode .grid-stack-item div,
.view-mode .grid-stack-item p,
.view-mode .grid-stack-item span {
  pointer-events: auto !important;
}

/* Allow page scrolling in view mode */
.view-mode.grid-stack {
  pointer-events: auto !important;
  overflow: auto !important;
}

.view-mode.grid-stack .grid-stack-item {
  pointer-events: auto !important;
}

/* Edit mode only - show resize handles and delete buttons */
:deep(.edit-mode .grid-stack-item .ui-resizable-handle) {
  display: none;
}

:deep(.edit-mode .grid-stack-item .ui-resizable-se) {
  display: block !important;
  position: absolute;
  bottom: 6px;
  right: 22px;
  width: 16px;
  height: 16px;
  background: transparent;
  transform: rotate(90deg);
  border: none;
  cursor: se-resize;
  z-index: 10;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

/* Show delete buttons in edit mode */
:deep(.edit-mode .grid-stack-item .remove-btn),
:deep(.edit-mode .grid-stack-item button.remove-btn),
:deep(.edit-mode .remove-btn),
:deep(.edit-mode .grid-stack-item .grid-stack-item-remove),
:deep(.edit-mode .grid-stack-item .pi-trash),
:deep(.edit-mode .grid-stack-item .pi-times),
:deep(.edit-mode .grid-stack-item [class*="remove"]),
:deep(.edit-mode .grid-stack-item [class*="delete"]),
:deep(.edit-mode .grid-stack-item [class*="close"]),
:deep(.edit-mode .grid-stack-item button[class*="trash"]),
:deep(.edit-mode .grid-stack-item i[class*="trash"]),
:deep(.edit-mode .grid-stack-item i[class*="times"]),
:deep(.edit-mode .grid-stack-item .delete-button),
:deep(.edit-mode .grid-stack-item .close-button),
:deep(.edit-mode .grid-stack-item .remove-button) {
  display: block !important;
  visibility: visible !important;
  opacity: 1 !important;
  pointer-events: auto !important;
}

/* View mode - completely hide all resize handles and delete buttons */
:deep(.view-mode .grid-stack-item .ui-resizable-handle),
:deep(.view-mode .grid-stack-item .ui-resizable-se) {
  display: none !important;
}

/* View mode - hide delete buttons */
:deep(.view-mode .grid-stack-item .remove-btn),
:deep(.view-mode .grid-stack-item button.remove-btn),
:deep(.view-mode .remove-btn),
:deep(.view-mode .grid-stack-item .grid-stack-item-remove),
:deep(.view-mode .grid-stack-item .pi-trash),
:deep(.view-mode .grid-stack-item .pi-times),
:deep(.view-mode .grid-stack-item [class*="remove"]),
:deep(.view-mode .grid-stack-item [class*="delete"]),
:deep(.view-mode .grid-stack-item [class*="close"]),
:deep(.view-mode .grid-stack-item button[class*="trash"]),
:deep(.view-mode .grid-stack-item i[class*="trash"]),
:deep(.view-mode .grid-stack-item i[class*="times"]),
:deep(.view-mode .grid-stack-item .delete-button),
:deep(.view-mode .grid-stack-item .close-button),
:deep(.view-mode .grid-stack-item .remove-button) {
  display: none !important;
  visibility: hidden !important;
  opacity: 0 !important;
  pointer-events: none !important;
}

:deep(.grid-stack-item .grid-stack-item-resize) {
  position: absolute;
  bottom: 2px;
  right: 8px;
  width: 16px;
  height: 16px;
  background: transparent;
  border: none;
  cursor: se-resize;
  z-index: 10;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.widget-body) {
  overflow: auto;
}

.widget-card {
  user-select: none;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.widget-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.widget-card:active {
  transform: scale(0.98);
  opacity: 0.8;
}

.widget-chart-area {
  height: 120px;
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

.widget-preview-content :deep(*) {
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

.grid-stack-item .widget-chart-area {
  height: 100%;
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
}

.grid-stack-item .widget-body {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.grid-stack-item .widget-body :deep(canvas),
.grid-stack-item .widget-body :deep(svg) {
  max-width: 100% !important;
  max-height: 100% !important;
  width: auto !important;
  height: auto !important;
  display: block !important;
  margin: auto !important;
  transition: all 0.3s ease;
}

.grid-stack-item .widget-body :deep(.apexcharts-canvas),
.grid-stack-item .widget-body :deep(.apexcharts-svg),
.grid-stack-item .widget-body :deep([class*="apex"]) {
  margin: auto !important;
  display: block !important;
  width: 100% !important;
  height: 100% !important;
}

.grid-stack-item .widget-body :deep(.chart-container),
.grid-stack-item .widget-body :deep([class*="chart"]) {
  width: 100% !important;
  height: 100% !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.grid-stack-item .grid-stack-item-content {
  transition: all 0.3s ease;
}

.grid-stack-item[gs-w="2"] .grid-stack-item-content .font-semibold {
  font-size: 0.75rem;
}

.grid-stack-item[gs-w="3"] .grid-stack-item-content .font-semibold {
  font-size: 0.875rem;
}

.grid-stack-item[gs-w="4"] .grid-stack-item-content .font-semibold {
  font-size: 1rem;
}

.grid-stack-item[gs-w="5"] .grid-stack-item-content .font-semibold {
  font-size: 1.125rem;
}

.grid-stack-item[gs-w="6"] .grid-stack-item-content .font-semibold {
  font-size: 1.25rem;
}

.grid-stack-item .widget-body :deep(*) {
  pointer-events: none;
}

:deep(.widget-html-content h3),
:deep(.widget-html-content p),
:deep(.widget-body h3),
:deep(.widget-body p) {
  display: none !important;
}

:deep(.widget-html-content .chart-container),
:deep(.widget-body .chart-container) {
  margin: 0 !important;
  padding: 0 !important;
}

/* Hide delete button in sidebar widgets */
.sidebar-widgets :deep(.pi-trash) {
  display: none !important;
}

.sidebar-widgets :deep(button[title="Delete Widget"]) {
  display: none !important;
}
</style>

