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
const renderedWidgetIds = new Set<string>()
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
  return savedWidgets.value.filter(w => {
    const name = w.dataInsightsComponent?.name?.toLowerCase() || ''
    const description = w.dataInsightsComponent?.description?.toLowerCase() || ''
    const searchLower = widgetSearchText.value.toLowerCase()
    return name.includes(searchLower) || description.includes(searchLower)
  })
})

const hasWidgetsInGrid = computed(() => {
  return hasWidgets.value
})

const isNewDashboard = computed(() => {
  return props.dashboardId === 'new' || !dashboard.value?.id
})

const saveButtonLabel = computed(() => {
  return "Save Dashboard"
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
            if (!gridStack.value) {
              return
            }
            
            gridStack.value.removeAll(false)
            gridStack.value.batchUpdate()
            gridStack.value.float(false)
            
            layoutData.widgets.forEach((widgetData: any) => {
              const widget = savedWidgets.value.find(w => w.id === widgetData.widgetId)
              
              if (widget) {
                addWidgetToGrid(
                  widget, 
                  widgetData.x, 
                  widgetData.y, 
                  widgetData.w, 
                  widgetData.h, 
                  widgetData.instanceId
                )
              }
            })
            
            setTimeout(() => {
              if (gridStack.value) {
                gridStack.value.commit()
                
                setTimeout(() => {
                  if (gridStack.value) {
                    gridStack.value.float(false)
                    if (!isEditMode.value) {
                      gridStack.value.setStatic(true)
                    }
                  }
            }, 2000)
              }
              addResizeIcons()
            }, 3000)
          }, 1500)
        }
      } catch (error) {
      }
    }
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const addResizeIcons = () => {
  const resizeHandles = document.querySelectorAll('.grid-stack-item .ui-resizable-se')
  
  resizeHandles.forEach((handle) => {
    if (handle.querySelector('i')) return
    
    const icon = document.createElement('i')
    icon.className = 'pi pi-arrow-up-right-and-arrow-down-left-from-center'
    icon.style.fontSize = '14px'
    icon.style.color = '#6b7280'
    icon.style.display = 'block'
    icon.style.lineHeight = '1'
    
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
  const gridOptions: any = {
    cellHeight: 71,
    column: 12,
    margin: 5,
    float: false,
    acceptWidgets: true,
    resizable: { handles: 'se' },
    draggable: { handle: '.grid-stack-item-content' },
    disableDrag: false,
    disableResize: false,
    animate: true,
    staticGrid: false,
    disableOneColumnMode: true
  }
  
  gridStack.value = GridStack.init(gridOptions)
  
  if (!isEditMode.value) {
    setTimeout(() => {
      if (gridStack.value) {
        gridStack.value.setStatic(true)
        gridStack.value.enableMove(false)
        gridStack.value.enableResize(false)
      }
    }, 100)
  }
  
  if (!isEditMode.value) {
    updateGridMode()
  }
  
  if (!isEditMode.value) {
    const hideDeleteButtons = () => {
      const gridItems = document.querySelectorAll('.grid-stack-item')
      gridItems.forEach((item) => {
        const resizeHandles = item.querySelectorAll('.ui-resizable-handle, .ui-resizable-se, .grid-stack-item-resize')
        resizeHandles.forEach((handle) => {
          const handleEl = handle as HTMLElement
          handleEl.style.display = 'none'
          handleEl.style.visibility = 'hidden'
        })
        
        const content = item.querySelector('.grid-stack-item-content')
        if (content) {
          const contentEl = content as HTMLElement
          contentEl.style.cursor = 'default'
          contentEl.style.overflow = 'auto'
          contentEl.style.height = '100%'
        }
        
        const itemEl = item as HTMLElement
        itemEl.style.overflow = 'auto'
        itemEl.style.position = 'absolute'
        itemEl.style.left = itemEl.style.left || '0px'
        itemEl.style.top = itemEl.style.top || '0px'
        itemEl.setAttribute('data-gs-locked', 'true')
        itemEl.setAttribute('data-gs-no-move', 'true')
        itemEl.setAttribute('data-gs-no-resize', 'true')
        
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
    
    setTimeout(hideDeleteButtons, 100)
    setTimeout(hideDeleteButtons, 500)
    setTimeout(hideDeleteButtons, 1000)
  } else {
    const showEditControls = () => {
      const gridItems = document.querySelectorAll('.grid-stack-item')
      gridItems.forEach((item) => {
        const deleteBtns = item.querySelectorAll('.remove-btn, button.remove-btn, .grid-stack-item-remove, .pi-trash, .pi-times, [class*="remove"], [class*="delete"], [class*="close"], button[class*="trash"], i[class*="trash"], i[class*="times"], .delete-button, .close-button, .remove-button')
        deleteBtns.forEach((btn) => {
          const btnEl = btn as HTMLElement
          btnEl.style.display = 'block'
          btnEl.style.visibility = 'visible'
          btnEl.style.opacity = '1'
          btnEl.style.pointerEvents = 'auto'
        })
        
        const content = item.querySelector('.grid-stack-item-content')
        if (content) {
          const contentEl = content as HTMLElement
          contentEl.style.cursor = 'move'
          contentEl.style.pointerEvents = 'auto'
        }
      })
    }
    
    setTimeout(showEditControls, 100)
    setTimeout(showEditControls, 500)
  }
  
  setTimeout(() => {
    addResizeIcons()
  }, 500)
  
  setupResizeIconObserver()
}

const setupDragDrop = () => {
  if (!gridStack.value) return
  
  console.log('🔧 Setting up GridStack drag-in...')
  
  // First, set widget sizes as GridStack attributes
  const updateWidgetAttributes = () => {
    const widgets = document.querySelectorAll('.sidebar-widgets .widget-card')
    console.log('📦 Found sidebar widgets:', widgets.length)
    
    widgets.forEach(card => {
      const widgetId = card.getAttribute('data-widget-id')
      if (!widgetId) return
      
      const widget = savedWidgets.value.find(w => w.id === widgetId)
      if (!widget) return
      
      const cardElement = card as HTMLElement
      cardElement.setAttribute('gs-w', '4')
      cardElement.setAttribute('gs-h', '4')
      cardElement.classList.add('grid-stack-item')
    })
  }
  
  updateWidgetAttributes()
  
  GridStack.setupDragIn('.sidebar-widgets .widget-card', {
    appendTo: 'body',
    helper: 'clone'
  })
  
  gridStack.value.on('dropped', (_event: any, _previousNode: any, newNode: any) => {
    if (!newNode || !newNode.el) {
      return
    }
    
    const droppedEl = newNode.el
    const widgetId = droppedEl.getAttribute('data-widget-id')
    
    if (widgetId) {
      const widget = savedWidgets.value.find(w => w.id === widgetId)
      
      if (widget) {
        gridStack.value?.removeWidget(droppedEl, true, false)
        
        setTimeout(() => {
          addWidgetToGrid(widget, newNode.x, newNode.y, newNode.w, newNode.h)
        }, 50)
      }
    }
  })
  
  const reRenderWidget = (element: HTMLElement) => {
    const instanceId = element.getAttribute('data-instance-id')
    const widgetId = element.getAttribute('data-widget-id')
    if (!instanceId || !widgetId) return
    
    const widget = savedWidgets.value.find(w => w.id === widgetId)
    if (!widget) return
    
    const widgetBody = element.querySelector('.widget-body')
    if (!widgetBody) return
    
    const loadingOverlay = element.querySelector('.widget-loading-overlay') as HTMLElement
    if (loadingOverlay) {
      loadingOverlay.style.display = 'flex'
      loadingOverlay.style.opacity = '1'
    }
    
    renderedWidgetIds.delete(instanceId)
    
    const htmlContent = widget.dataInsightsComponent?.rawHtml || ''
    if (!htmlContent || !htmlContent.includes('customElements.define')) return
    
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    const storedElementName = element.getAttribute('data-element-name')
    const elementName = storedElementName || (elementNameMatch ? elementNameMatch[1] : null)
    
    if (!elementName || !customElements.get(elementName)) return
    
    widgetBody.innerHTML = ''
    const chartElement = document.createElement(elementName)
    widgetBody.appendChild(chartElement)
    
    renderedWidgetIds.add(instanceId)
    
    setTimeout(() => {
      if (chartElement.shadowRoot) {
        const style = document.createElement('style')
        style.textContent = `
          h1, h2, h3, h4, h5, h6, p, span:not([class*="apex"]), label, title, desc, 
          .title, .description, .container h3, .container p { 
            display: none !important; 
            visibility: hidden !important;
            height: 0 !important;
          }
          :host { 
            padding: 0 !important; 
            margin: 0 !important; 
            border: none !important; 
            box-shadow: none !important; 
            background: transparent !important;
          }
          .container {
            border: none !important;
            padding: 0 !important;
            background: transparent !important;
          }
          canvas, svg, .chart-container { 
            display: block !important; 
            max-width: 100% !important;
            max-height: 100% !important;
          }
        `
        chartElement.shadowRoot.appendChild(style)
        
        setTimeout(() => {
          if (chartElement.shadowRoot) {
            const canvas = chartElement.shadowRoot.querySelector('canvas') as any
            if (canvas && canvas.__ec_inner__) {
              canvas.__ec_inner__.resize()
            }
          }
          
          setTimeout(() => {
            const loadingOverlay = element.querySelector('.widget-loading-overlay') as HTMLElement
            if (loadingOverlay) {
              loadingOverlay.style.opacity = '0'
              setTimeout(() => {
                loadingOverlay.style.display = 'none'
              }, 300)
            }
          }, 200)
        }, 100)
      }
    }, 100)
  }
  
  gridStack.value.on('resizestop', (_event: any, el: any) => {
    const element = el.el || el
    reRenderWidget(element)
  })
  
  gridStack.value.on('resize', (_event: any, el: any) => {
    const element = el.el || el
    const widgetBody = element.querySelector('.widget-body')
    if (widgetBody) {
      const chartElement = widgetBody.querySelector('*')
      if (chartElement && (chartElement as any).shadowRoot) {
        const canvas = (chartElement as any).shadowRoot.querySelector('canvas') as any
        if (canvas && canvas.__ec_inner__) {
          canvas.__ec_inner__.resize()
        }
      }
    }
  })
}

const loadEchartsForGrid = (): Promise<void> => {
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

const addWidgetToGrid = async (widget: DataInsightsWidget, x?: number, y?: number, w?: number, h?: number, instanceId?: string) => {
  if (!gridStack.value || !widget.id) return
  const widgetInstanceId = instanceId || `${widget.id}-${Date.now()}`

  const htmlContent = widget.dataInsightsComponent?.rawHtml || ''
  const widgetTitle = widget.dataInsightsComponent?.name || 'Untitled Widget'
  const widgetSubtitle = widget.dataInsightsComponent?.description || ''
  
  if (htmlContent.includes('echarts')) {
    await loadEchartsForGrid()
  }

  let finalElementName = null

  if (htmlContent && htmlContent.includes('customElements.define')) {
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    let elementName = elementNameMatch ? elementNameMatch[1] : null
    
    if (elementName) {
      if (!customElements.get(elementName)) {
      try {
        eval(htmlContent)
          finalElementName = elementName
      } catch (error) {
        }
      } else {
        const newClassMatch = htmlContent.match(/class\s+(\w+)\s+extends\s+HTMLElement/)
        const existingElement = customElements.get(elementName)
        
        if (newClassMatch && existingElement && existingElement.name !== newClassMatch[1]) {
          const uniqueName = `${elementName}-${widgetInstanceId.substring(0, 8)}`
          const modifiedHtml = htmlContent.replace(
            `customElements.define('${elementName}'`,
            `customElements.define('${uniqueName}'`
          )
          try {
            eval(modifiedHtml)
            finalElementName = uniqueName
          } catch (error) {
            finalElementName = elementName
          }
        } else {
          finalElementName = elementName
        }
      }
    }
  }

  const el = document.createElement('div')
  el.className = 'grid-stack-item'
  el.setAttribute('data-widget-id', widget.id)
  el.setAttribute('data-instance-id', widgetInstanceId)
  el.setAttribute('data-element-name', finalElementName || '')
  const escapedTitle = widgetTitle.replace(/"/g, '&quot;').replace(/'/g, '&#39;')
  const escapedSubtitle = widgetSubtitle.replace(/"/g, '&quot;').replace(/'/g, '&#39;')
  
  el.innerHTML = `
    <div class="grid-stack-item-content bg-white rounded-lg border border-surface-200 h-full flex flex-col p-4 relative">
      <div class="widget-loading-overlay absolute inset-0 flex items-center justify-center bg-white z-20 rounded-lg">
          <div class="text-center">
          <i class="pi pi-spin pi-spinner text-blue-500 text-3xl mb-3"></i>
          <div class="text-sm font-medium text-gray-700">Loading widget...</div>
          </div>
        </div>
      
      <div class="widget-header mb-2 pb-2">
        <h4 class="text-base font-semibold text-gray-900 mb-1">${escapedTitle}</h4>
        ${widgetSubtitle ? `<p class="text-xs text-gray-600 line-clamp-2">${escapedSubtitle}</p>` : ''}
      </div>
      <div class="widget-chart-area flex-1 relative">
        <div class="widget-body h-full w-full" data-instance-id="${widgetInstanceId}"></div>
      </div>
      
      <button class="remove-btn fixed-button" style="position: absolute !important; top: 8px !important; right: 8px !important; z-index: 100 !important;">
        <i class="pi pi-trash"></i>
      </button>
    </div>
  `

  const removeBtn = el.querySelector('.remove-btn')
  if (removeBtn) {
    removeBtn.addEventListener('click', async () => {
      gridStack.value?.removeWidget(el)
      addedWidgetIds.delete(widgetInstanceId)
      renderedWidgetIds.delete(widgetInstanceId) // Clean up render tracking
      
      setTimeout(() => {
        const remainingWidgets = document.querySelectorAll('.grid-stack-item')
        hasWidgets.value = remainingWidgets.length > 0
      }, 100)
    })
  }
  const defaultSize = { w: 4, h: 4 }
  const options: any = { 
    w: w || defaultSize.w, 
    h: h || defaultSize.h,
    minW: 1,
    maxW: 12,
    minH: 1,
    maxH: 12,
    autoPosition: x === undefined && y === undefined
  }
  
  if (x !== undefined) {
    options.x = x
  }
  if (y !== undefined) {
    options.y = y
  }
  
  gridStack.value.makeWidget(el, options)
  
        setTimeout(() => {
    const actualX = el.getAttribute('gs-x')
    const actualY = el.getAttribute('gs-y')
    
    if (x !== undefined && actualX !== x.toString()) {
      el.setAttribute('gs-x', x.toString())
      el.style.left = `${x * (100 / 12)}%`
    }
    if (y !== undefined && actualY !== y.toString()) {
      el.setAttribute('gs-y', y.toString())
      const cellHeight = 71 + 5
      el.style.top = `${y * cellHeight}px`
    }
  }, 10)
  addedWidgetIds.add(widgetInstanceId)
  hasWidgets.value = true
  
  // Function to render widget chart (used by both initial load and resize)
  const renderWidgetChart = (targetEl: HTMLElement) => {
    const widgetBody = targetEl.querySelector('.widget-body')
    if (!widgetBody) return
    
    const widgetId = targetEl.getAttribute('data-widget-id')
    const instanceId = targetEl.getAttribute('data-instance-id')
    if (!widgetId || !instanceId) return
    
    if (renderedWidgetIds.has(instanceId)) {
      return
    }
    
    const widget = savedWidgets.value.find(w => w.id === widgetId)
    if (!widget) return
    
    const htmlContent = widget.dataInsightsComponent?.rawHtml || ''
    if (!htmlContent || !htmlContent.includes('customElements.define')) return
    
    const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
    const storedElementName = targetEl.getAttribute('data-element-name')
    const elementName = storedElementName || (elementNameMatch ? elementNameMatch[1] : null)
    
    if (!elementName || !customElements.get(elementName)) return
    
          widgetBody.innerHTML = ''
    const element = document.createElement(elementName)
          widgetBody.appendChild(element)
    
    renderedWidgetIds.add(instanceId)
          
          setTimeout(() => {
            if (element.shadowRoot) {
              const style = document.createElement('style')
              style.textContent = `
          h1, h2, h3, h4, h5, h6, p, span:not([class*="apex"]), label, title, desc, 
          .title, .description, .container h3, .container p { 
                  display: none !important;
                  visibility: hidden !important;
                  height: 0 !important;
          }
          :host { 
                  padding: 0 !important;
            margin: 0 !important; 
            border: none !important; 
            box-shadow: none !important; 
            background: transparent !important;
                }
                .container {
                  border: none !important;
            padding: 0 !important;
                  background: transparent !important;
                }
          canvas, svg, .chart-container { 
            display: block !important; 
            max-width: 100% !important;
            max-height: 100% !important;
                }
              `
              element.shadowRoot.appendChild(style)
              
              setTimeout(() => {
          if (element.shadowRoot) {
            const canvas = element.shadowRoot.querySelector('canvas') as any
            if (canvas && canvas.__ec_inner__) {
              canvas.__ec_inner__.resize()
            }
          }
          
          // Hide loading overlay
          const loadingOverlay = targetEl.querySelector('.widget-loading-overlay') as HTMLElement
          if (loadingOverlay) {
            loadingOverlay.style.opacity = '0'
            setTimeout(() => {
              loadingOverlay.style.display = 'none'
            }, 300)
          }
        }, 100)
      }
    }, 100)
  }
  
  const resizeObserver = new ResizeObserver((entries) => {
    entries.forEach((entry) => {
      const targetEl = entry.target as HTMLElement
      const instanceId = targetEl.getAttribute('data-instance-id')
      
      if (!instanceId) return
      
      if (!renderedWidgetIds.has(instanceId)) {
        setTimeout(() => {
          renderWidgetChart(targetEl)
        }, 100)
    } else {
        setTimeout(() => {
          const widgetBody = targetEl.querySelector('.widget-body')
          if (widgetBody) {
            const element = widgetBody.querySelector('*')
            if (element && (element as any).shadowRoot) {
              const canvas = (element as any).shadowRoot.querySelector('canvas') as any
              if (canvas && canvas.__ec_inner__) {
                canvas.__ec_inner__.resize()
              }
            }
          }
        }, 50)
      }
    })
  })
  
  resizeObserver.observe(el)
  
  // Trigger initial render
  setTimeout(() => {
    renderWidgetChart(el)
  }, 500)
  
  setTimeout(() => {
    addResizeIcons()
  }, 800)
  
  setTimeout(() => {
    addResizeIcons()
  }, 1500)
}

const createDashboard = async () => {
  if (!dashboardTitle.value || dashboardTitle.value.trim() === '') {
    toast.add({ severity: 'warn', summary: 'Warning', detail: 'Please enter a dashboard title', life: 3000 })
    return
  }
  
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
      const h = parseInt(el.getAttribute('gs-h') || '4')
      
      if (widgetId && instanceId) {
        widgetInstances.push({ instanceId, widgetId, x, y, w, h })
      }
    })
    
    const layoutJson = JSON.stringify({ widgets: widgetInstances })
    
    const newDashboard: any = {
      id: null,
      name: dashboardTitle.value.trim(),
      description: 'Dashboard',
      applicationId: props.applicationId,
      layout: layoutJson,
      created: new Date(),
      updated: new Date()
    }
    
    const savedDashboard = await dashboardService.save(newDashboard)
    
    toast.add({ severity: 'success', summary: 'Created', detail: `Dashboard "${savedDashboard.name}" created successfully`, life: 3000 })
    
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
      const h = parseInt(el.getAttribute('gs-h') || '4')
      
      if (widgetId && instanceId) {
        widgetInstances.push({ instanceId, widgetId, x, y, w, h })
      }
    })
    
    const layoutJson = JSON.stringify({ widgets: widgetInstances })
    
    dashboard.value.name = dashboardTitle.value
    dashboard.value.layout = layoutJson
    dashboard.value.updated = new Date()
    
    await dashboardService.save(dashboard.value)
    
    toast.add({ severity: 'success', summary: 'Updated', detail: 'Dashboard updated successfully', life: 3000 })
    
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

const clearDateRange = () => {
  dateRange.value = { startDate: null, endDate: null }
  updateDateRange()
  
  setTimeout(() => {
    window.dispatchEvent(new Event('resize'))
  }, 100)
}

const toggleDateRangePicker = () => {
  showDateRangePicker.value = !showDateRangePicker.value
}

const enterEditMode = () => {
  router.push(`/application/${props.applicationId}/dashboards/${props.dashboardId}/edit`)
}

const updateGridMode = () => {
  if (!gridStack.value) return
  
  if (!isEditMode.value) {
    gridStack.value.float(false)
    gridStack.value.setStatic(true)
    gridStack.value.enableMove(false)
    gridStack.value.enableResize(false)
  } else {
    gridStack.value.setStatic(false)
    gridStack.value.float(false)
    gridStack.value.enableMove(true)
    gridStack.value.enableResize(true)
  }
}

watch(isEditMode, () => {
  if (gridStack.value) {
    updateGridMode()
    
    if (isEditMode.value) {
      gridStack.value.enableMove(true)
      gridStack.value.enableResize(true)
      setTimeout(() => setupDragDrop(), 100)
    } else {
      gridStack.value.enableMove(false)
      gridStack.value.enableResize(false)
      
      setTimeout(() => {
        const gridItems = document.querySelectorAll('.grid-stack-item')
        gridItems.forEach((item) => {
          const element = item as HTMLElement
          const instanceId = element.getAttribute('data-instance-id')
          const widgetId = element.getAttribute('data-widget-id')
          
          if (instanceId && widgetId) {
            const widget = savedWidgets.value.find(w => w.id === widgetId)
            if (!widget) return
            
            const widgetBody = element.querySelector('.widget-body')
            if (!widgetBody) return
            
            renderedWidgetIds.delete(instanceId)
            
            const htmlContent = widget.dataInsightsComponent?.rawHtml || ''
            if (!htmlContent || !htmlContent.includes('customElements.define')) return
            
            const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
            const storedElementName = element.getAttribute('data-element-name')
            const elementName = storedElementName || (elementNameMatch ? elementNameMatch[1] : null)
            
            if (!elementName || !customElements.get(elementName)) return
            
            widgetBody.innerHTML = ''
            const chartElement = document.createElement(elementName)
            widgetBody.appendChild(chartElement)
            
            renderedWidgetIds.add(instanceId)
            
            setTimeout(() => {
              if (chartElement.shadowRoot) {
                const style = document.createElement('style')
                style.textContent = `
                  h1, h2, h3, h4, h5, h6, p, span:not([class*="apex"]), label, title, desc, 
                  .title, .description, .container h3, .container p { 
                    display: none !important; 
                    visibility: hidden !important;
                    height: 0 !important;
                  }
                  :host { 
                    padding: 0 !important; 
                    margin: 0 !important; 
                    border: none !important; 
                    box-shadow: none !important; 
                    background: transparent !important;
                  }
                  .container {
                    border: none !important;
                    padding: 0 !important;
                    background: transparent !important;
                  }
                  canvas, svg, .chart-container { 
                    display: block !important; 
                    max-width: 100% !important;
                    max-height: 100% !important;
                  }
                `
                chartElement.shadowRoot.appendChild(style)
                
                setTimeout(() => {
                  if (chartElement.shadowRoot) {
                    const canvas = chartElement.shadowRoot.querySelector('canvas') as any
                    if (canvas && canvas.__ec_inner__) {
                      canvas.__ec_inner__.resize()
                    }
                  }
                }, 100)
              }
            }, 100)
          }
        })
      }, 500)
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
      setTimeout(() => {
        setupDragDrop()
      }, 500)
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
          <div v-if="!isEditMode">
            <h1 class="text-xl font-semibold text-surface-900">{{ headerTitle }}</h1>
            <p class="text-sm text-surface-500">{{ headerSubtitle }}</p>
          </div>
          <InputText v-if="isEditMode" v-model="dashboardTitle" placeholder="Dashboard Title" class="text-xl font-semibold" />
        </div>
        <div class="flex gap-2 items-center">
          <div v-if="!isEditMode" class="flex items-center gap-2">
            <Button
              @click="toggleDateRangePicker"
              :class="showDateRangePicker ? '!bg-blue-600 !border-blue-600 !text-white !hover:bg-blue-700 !hover:border-blue-700 !rounded-md !px-4 !h-[33px] !font-medium !text-[14px] [&_.pi]:!w-[14px] [&_.pi]:!h-[14px]' : '!bg-white !border !border-gray-300 !text-gray-700 !hover:bg-gray-50 !hover:border-gray-400 !rounded-md !px-4 !h-[33px] !font-medium !text-[14px] [&_.pi]:!w-[14px] [&_.pi]:!h-[14px]'"
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
                @click="clearDateRange"
                icon="pi pi-times"
                size="small"
                class="p-button-text"
                title="Clear date range"
              />
            </div>
          </div>
          
          <Button v-if="!isEditMode" @click="enterEditMode" label="Edit" icon="pi pi-pencil" 
                  class="!bg-white !border !border-gray-300 !text-gray-700 !hover:bg-gray-50 !hover:border-gray-400 !rounded-md !px-4 !h-[33px] !font-medium !text-[14px] [&_.pi]:!w-[14px] [&_.pi]:!h-[14px]" />
          <template v-if="isEditMode">
            <Button @click="goBack" label="Cancel" 
                    class="!bg-gray-200 !border-gray-200 !text-gray-700 !hover:bg-gray-300 !hover:border-gray-300 !rounded-md !px-4 !h-[33px] !font-medium !text-[14px]" />
            <Button @click="saveDashboard" :label="saveButtonLabel" 
                    class="!bg-blue-600 !border-blue-600 !text-white !hover:bg-blue-700 !hover:border-blue-700 !rounded-md !px-4 !h-[33px] !font-medium !text-[14px]" />
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
               <Button @click="enterEditMode" label="Add Widgets" 
                       class="bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300 rounded-md px-3 py-2 font-medium" />
             </div>
           </div>
         </div>
       </div>
    </div>

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
.container {
 border: none !important;
}
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

/* GridStack placeholder styling - shown when dragging */
:deep(.grid-stack > .grid-stack-placeholder) {
  background: rgba(59, 130, 246, 0.15) !important;
  border: 2px dashed #3b82f6 !important;
  border-radius: 8px !important;
  opacity: 1 !important;
  visibility: visible !important;
  display: block !important;
}

:deep(.grid-stack > .grid-stack-placeholder > .placeholder-content) {
  background: transparent !important;
  border: none !important;
}

/* Custom placeholder content */
.grid-stack-placeholder::before {
  content: 'Drop here' !important;
  position: absolute !important;
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%) !important;
  color: #3b82f6 !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  text-transform: uppercase !important;
  letter-spacing: 1px !important;
  z-index: 10 !important;
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

.view-mode.grid-stack {
  pointer-events: auto !important;
  overflow: auto !important;
}

.view-mode.grid-stack .grid-stack-item {
  pointer-events: auto !important;
}

:deep(.edit-mode .grid-stack-item .ui-resizable-handle) {
  display: none;
}


:deep(.grid-stack > .grid-stack-item[gs-w="4"][gs-h="4"]) {
  width: 414px !important;
  height: 305px !important;
}

:deep(.edit-mode .grid-stack-item .ui-resizable-se) {
  display: flex !important;
  position: absolute !important;
  bottom: 10px !important;
  right: 10px !important;
  width: 24px !important;
  height: 24px !important;
  background: rgba(255, 255, 255, 0.95) !important;
  transform: rotate(90deg) !important;
  cursor: se-resize !important;
  z-index: 999999 !important;
  align-items: center !important;
  justify-content: center !important;
  pointer-events: auto !important;
  opacity: 1 !important;
  visibility: visible !important;
}

:deep(.edit-mode .grid-stack-item .ui-resizable-se:hover) {
  background: rgba(59, 130, 246, 0.1) !important;
  border-color: #3b82f6 !important;
}

/* Show delete buttons in edit mode */
.remove-btn.fixed-button {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 28px !important;
  height: 28px !important;
  border-radius: 4px !important;
  background: white !important;
  border: 1px solid #e5e7eb !important;
  cursor: pointer !important;
  transition: all 0.2s ease !important;
}

.remove-btn.fixed-button:hover {
  background: #fee2e2 !important;
  border-color: #ef4444 !important;
  color: #ef4444 !important;
}

:deep(.edit-mode .grid-stack-item .remove-btn),
:deep(.edit-mode .grid-stack-item button.remove-btn),
:deep(.edit-mode .remove-btn),
:deep(.edit-mode .grid-stack-item .grid-stack-item-remove) {
  display: flex !important;
  visibility: visible !important;
  opacity: 1 !important;
  pointer-events: auto !important;
  position: absolute !important;
  top: 8px !important;
  right: 8px !important;
  z-index: 100 !important;
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
  background: transparent;
  border: none !important;
}

.grid-stack-item .widget-body {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: none !important;
  background: transparent !important;
}

.grid-stack-item .widget-body :deep(*) {
  border: none !important;
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
:deep(.widget-body p),
:deep(.widget-body h1),
:deep(.widget-body h2),
:deep(.widget-body h4),
:deep(.widget-body h5),
:deep(.widget-body h6) {
  display: none !important;
  visibility: hidden !important;
  opacity: 0 !important;
  height: 0 !important;
}

:deep(.widget-html-content .chart-container),
:deep(.widget-body .chart-container),
:deep(.widget-body .container),
:deep(.widget-body div) {
  margin: 0 !important;
  padding: 0 !important;
  border: none !important;
  box-shadow: none !important;
}

/* Loading overlay styling */
.widget-loading-overlay {
  background: rgba(255, 255, 255, 0.98);
  transition: opacity 0.3s ease;
  backdrop-filter: blur(2px);
}

.widget-loading-overlay.hidden {
  opacity: 0;
  pointer-events: none;
}

/* Hide delete button in sidebar widgets */
.sidebar-widgets :deep(.pi-trash) {
  display: none !important;
}

.sidebar-widgets :deep(button[title="Delete Widget"]) {
  display: none !important;
}
</style>

