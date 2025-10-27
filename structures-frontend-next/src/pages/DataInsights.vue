<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Card from 'primevue/card'
import ScrollPanel from 'primevue/scrollpanel'
import Divider from 'primevue/divider'
import Calendar from 'primevue/calendar'
import { Structures, ProgressType } from '@kinotic/structures-api'
import type { InsightRequest, DataInsightsComponent, InsightProgress } from '@kinotic/structures-api'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { INSIGHTS_STATE, type InsightData } from '@/states/IInsightsState'
import { DataInsightsWidgetEntityService } from '@/services/DataInsightsWidgetEntityService'
import { DataInsightsWidget } from '@/domain/DataInsightsWidget'

interface ChatMessage {
  id: string
  type: 'user' | 'assistant'
  content: string
  timestamp: Date
  loading?: boolean
  tasks?: string[]
  isExpanded?: boolean
}

interface VisualizationComponent {
  id: string
  htmlContent: string
  createdAt: Date
  status: string
  supportsDateRangeFiltering?: boolean
  saved?: boolean
  component?: DataInsightsComponent
  userQuery?: string
}

interface DateRange {
  startDate: Date | null
  endDate: Date | null
}

class DateRangeObservable {
  private subscribers: Set<(dateRange: DateRange) => void> = new Set()
  private currentDateRange: DateRange = {
    startDate: null,
    endDate: null
  }

  subscribe(callback: (dateRange: DateRange) => void): () => void {
    this.subscribers.add(callback)
    callback(this.currentDateRange)
    return () => {
      this.subscribers.delete(callback)
    }
  }

  updateDateRange(dateRange: DateRange) {
    this.currentDateRange = dateRange
    this.subscribers.forEach(callback => callback(dateRange))
  }

  getCurrentDateRange(): DateRange {
    return { ...this.currentDateRange }
  }
}
declare global {
  interface Window {
    globalDateRangeObservable: DateRangeObservable
  }
}

@Component({
  components: {
    InputText,
    Button,
    Card,
    ScrollPanel,
    Divider,
    Calendar
  }
})
export default class DataInsights extends Vue {
  chatMessages: ChatMessage[] = []
  userInput: string = ''
  isLoading: boolean = false
  visualizations: VisualizationComponent[] = []
  currentApplicationId: string = ''
  dateRange: DateRange = {
    startDate: null,
    endDate: null
  }
  showDateRangePicker: boolean = false
  widgetService: DataInsightsWidgetEntityService = new DataInsightsWidgetEntityService()

  get currentApplicationName(): string {
    const appId = APPLICATION_STATE.currentApplication?.id
    return appId || 'Unknown Application'
  }

  mounted() {
    if (!window.globalDateRangeObservable) {
      window.globalDateRangeObservable = new DateRangeObservable()
    }
    
    this.currentApplicationId = APPLICATION_STATE.currentApplication?.id || this.$route.params.applicationId as string || 'default'
    
    const routeAppId = this.$route.params.applicationId as string
    
    if (routeAppId && !APPLICATION_STATE.currentApplication) {
      const app = APPLICATION_STATE.allApplications?.find(a => a.id === routeAppId)
      if (app) {
        APPLICATION_STATE.currentApplication = app
      } else {
        this.currentApplicationId = routeAppId
      }
    }
    
    (window as any).setCurrentApp = this.setCurrentApplication
    
    this.restoreStateFromStoredInsights()
  }

  setCurrentApplication(appId: string): void {
    const app = APPLICATION_STATE.allApplications?.find(a => a.id === appId)
    if (app) {
      APPLICATION_STATE.currentApplication = app
      this.currentApplicationId = app.id
      this.chatMessages = []
      this.visualizations = []
      const dashboardContainer = document.getElementById('dashboard-container')
      if (dashboardContainer) {
        dashboardContainer.innerHTML = ''
      }
      this.addWelcomeMessage()
    }
  }

  restoreStateFromStoredInsights(): void {
    this.visualizations = []
    this.chatMessages = []
    
    const storedInsights = INSIGHTS_STATE.getInsightsByApplication(this.currentApplicationId)
    
    if (storedInsights.length === 0) {
      this.addWelcomeMessage()
      return
    }
    this.visualizations = storedInsights
      .filter(insight => insight.htmlContent)
      .map(insight => ({
        id: insight.id,
        htmlContent: insight.htmlContent!,
        createdAt: insight.createdAt,
        status: 'success',
        supportsDateRangeFiltering: false
      }))
    this.addWelcomeMessage()
    this.chatMessages.push({
      id: 'restored-summary',
      type: 'assistant',
      content: `Restored ${storedInsights.length} previous analysis${storedInsights.length > 1 ? 'es' : ''}. Your visualizations are available in the dashboard.`,
      timestamp: new Date(),
      loading: false,
      tasks: [],
      isExpanded: false
    })
     setTimeout(() => {
       this.visualizations.forEach(viz => {
         this.executeVisualization(viz.htmlContent, viz.id)
       })
     }, 1000)
  }

  addWelcomeMessage() {
    this.chatMessages.push({
      id: 'welcome',
      type: 'assistant',
      content: `Hello! I'm your data insights assistant. 

Ask me questions about your data in application "${this.currentApplicationName}" and I'll create visualizations for you. Try asking things like:

• Show me a summary of my data
• Create a chart showing trends over time
• Display the most important metrics

Components that support date filtering will automatically respond to the global date range picker.`,
      timestamp: new Date()
    })
  }

  @Watch('APPLICATION_STATE.currentApplication', { immediate: true, deep: true })
  onApplicationChange() {
    if (APPLICATION_STATE.currentApplication) {
      this.currentApplicationId = APPLICATION_STATE.currentApplication.id
      this.chatMessages = []
      this.visualizations = []
      const dashboardContainer = document.getElementById('dashboard-container')
      if (dashboardContainer) {
        dashboardContainer.innerHTML = ''
      }
      this.restoreStateFromStoredInsights()
    }
  }
  @Watch('currentApplicationName', { immediate: true })
  onApplicationNameChange(newName: string, oldName: string) {
    if (newName !== 'Unknown Application' && newName !== oldName) {
      this.currentApplicationId = APPLICATION_STATE.currentApplication?.id || ''
      this.chatMessages = []
      this.visualizations = []
      const dashboardContainer = document.getElementById('dashboard-container')
      if (dashboardContainer) {
        dashboardContainer.innerHTML = ''
      }
      
      this.addWelcomeMessage()
    }
  }

  updateDateRange() {
    window.globalDateRangeObservable.updateDateRange(this.dateRange)
  }

  toggleDateRangePicker() {
    this.showDateRangePicker = !this.showDateRangePicker
  }

  async sendMessage() {
    if (!this.userInput.trim() || this.isLoading) return

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      type: 'user',
      content: this.userInput,
      timestamp: new Date()
    }

    this.chatMessages.push(userMessage)
    const userQuery = this.userInput
    this.userInput = ''
    this.isLoading = true

    const loadingMessage: ChatMessage = {
      id: (Date.now() + 1).toString(),
      type: 'assistant',
      content: 'Starting analysis...',
      timestamp: new Date(),
      loading: true,
      tasks: []
    }
    this.chatMessages.push(loadingMessage)

    try {
      const insightsService = Structures.getDataInsightsService()
      
      const request: InsightRequest = {
        query: userQuery,
        applicationId: this.currentApplicationId,
        focusStructureId: undefined,
        preferredVisualization: undefined,
        additionalContext: undefined
      }

      const progressObservable = insightsService.processRequest(request)
      
      progressObservable.subscribe({
        next: (progress: InsightProgress) => {
          const loadingMessage = this.chatMessages.find(msg => msg.loading)
          if (loadingMessage) {
            if (progress.message && !loadingMessage.tasks?.includes(progress.message)) {
              if (!loadingMessage.tasks) {
                loadingMessage.tasks = []
              }
              loadingMessage.tasks.push(progress.message)
            }
            loadingMessage.content = progress.message
          }
          
           if (progress.type === ProgressType.COMPONENTS_READY && progress.components && progress.components.length > 0) {
             progress.components.forEach(async (component: DataInsightsComponent) => {
               this.visualizations.push({
                 id: component.id,
                 htmlContent: component.rawHtml,
                 createdAt: new Date(component.modifiedAt),
                 status: 'success',
                 supportsDateRangeFiltering: component.supportsDateRangeFiltering || false,
                 saved: false,
                 component: component,
                 userQuery: userQuery
               })

               const insightData: InsightData = {
                 id: component.id,
                 title: component.name,
                 description: `AI-generated insight for: "${userQuery}"`,
                 query: userQuery,
                 applicationId: this.currentApplicationId,
                 createdAt: new Date(component.modifiedAt),
                 data: component,
                 visualizationType: this.detectVisualizationType(component.rawHtml),
                 htmlContent: component.rawHtml,
                 metadata: {
                   tasks: loadingMessage?.tasks,
                   status: 'success'
                 }
               }
               INSIGHTS_STATE.addInsight(insightData)

               this.executeVisualization(component.rawHtml, component.id)
             })
           }
          
          if (progress.type === ProgressType.COMPLETED) {
            const loadingMessage = this.chatMessages.find(msg => msg.loading)
            if (loadingMessage) {
              loadingMessage.loading = false
              loadingMessage.content = 'Analysis completed! Your visualizations have been added to the dashboard.'
              loadingMessage.isExpanded = false 
            }
          }
          
          if (progress.type === ProgressType.ERROR) {
            const loadingMessage = this.chatMessages.find(msg => msg.loading)
            if (loadingMessage) {
              loadingMessage.loading = false
              loadingMessage.content = `Error: ${progress.errorMessage || 'An error occurred during analysis'}`
              loadingMessage.isExpanded = false
            }
          }
        },
        error: (error) => {
          const loadingMessage = this.chatMessages.find(msg => msg.loading)
          if (loadingMessage) {
            loadingMessage.loading = false
            loadingMessage.content = `Error: ${error.message || 'An error occurred during analysis'}`
            loadingMessage.isExpanded = false
          }
        },
        complete: () => {
          this.isLoading = false
        }
      })
    } catch (error) {
      const loadingMessage = this.chatMessages.find(msg => msg.loading)
      if (loadingMessage) {
        loadingMessage.loading = false
        loadingMessage.content = `Sorry, I couldn't process your request. Please try again or rephrase your question.`
        loadingMessage.isExpanded = false
      }
      
      this.isLoading = false
    }
  }

  executeVisualization(htmlContent: string, componentId?: string) {
    try {
      
      const script = document.createElement('script')
      script.textContent = htmlContent
      document.head.appendChild(script)
      
      const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
      const elementName = elementNameMatch ? elementNameMatch[1] : 'data-insights-dashboard'
      
      setTimeout(() => {
        try {
          if (!customElements.get(elementName)) {
            return
          }
          
          const wrapper = document.createElement('div')
          wrapper.className = 'visualization-wrapper relative'
          wrapper.setAttribute('data-viz-id', componentId || '')
          
          const saveButton = document.createElement('button')
          saveButton.className = 'save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
          saveButton.innerHTML = '<i class="pi pi-bookmark text-base"></i>'
          saveButton.onclick = () => this.handleSaveWidget(componentId!)
          
          const element = document.createElement(elementName)
          
          wrapper.appendChild(saveButton)
          wrapper.appendChild(element)
          
          const dashboardContainer = document.getElementById('dashboard-container')
          if (dashboardContainer) {
            dashboardContainer.appendChild(wrapper)
            
          } else {
          }
        } catch (error) {
        }
      }, 1000)
    } catch (error) {
    }
  }

  onKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      this.sendMessage()
    }
  }

  generateInsightTitle(query: string): string {
    // Generate a meaningful title from the user query
    const words = query.toLowerCase().split(' ')
    const keyWords = words.filter(word => 
      !['show', 'me', 'create', 'display', 'generate', 'make', 'a', 'an', 'the', 'of', 'for', 'with', 'in', 'on', 'at', 'to', 'from'].includes(word)
    )
    
    if (keyWords.length > 0) {
      return keyWords.slice(0, 3).map(word => 
        word.charAt(0).toUpperCase() + word.slice(1)
      ).join(' ')
    }
    
    return 'Data Insight'
  }

  detectVisualizationType(htmlContent: string): 'chart' | 'table' | 'stat' | 'list' {
    const content = htmlContent.toLowerCase()
    
    if (content.includes('chart') || content.includes('apexchart') || content.includes('canvas')) {
      return 'chart'
    } else if (content.includes('table') || content.includes('<table>') || content.includes('thead')) {
      return 'table'
    } else if (content.includes('stat') || content.includes('metric') || content.includes('number')) {
      return 'stat'
    } else {
      return 'list'
    }
  }

  async handleSaveWidget(componentId: string): Promise<void> {
    const visualization = this.visualizations.find(v => v.id === componentId)
    
    if (!visualization || !visualization.component || !visualization.userQuery) {
      return
    }
    
    if (visualization.saved) {
      return
    }
    
    try {
      // Update button to show saving state
      const wrapper = document.querySelector(`[data-viz-id="${componentId}"]`)
      const saveButton = wrapper?.querySelector('.save-widget-btn') as HTMLButtonElement
      if (saveButton) {
        saveButton.disabled = true
        saveButton.innerHTML = '<i class="pi pi-spin pi-spinner text-base"></i>'
      }
      
      await this.saveWidgetAsEntity(visualization.component)
      
      // Mark as saved
      visualization.saved = true
      
      // Update button to show saved state
      if (saveButton) {
        saveButton.className = 'save-widget-btn absolute top-2 right-2 bg-gray-200 text-gray-600 rounded p-2 shadow-sm z-10 flex items-center justify-center w-10 h-10 cursor-default'
        saveButton.innerHTML = '<i class="pi pi-bookmark-fill text-base"></i>'
        saveButton.disabled = true
      }
      
    } catch (error) {
      
      const wrapper = document.querySelector(`[data-viz-id="${componentId}"]`)
      const saveButton = wrapper?.querySelector('.save-widget-btn') as HTMLButtonElement
      if (saveButton) {
        saveButton.disabled = false
        saveButton.className = 'save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
        saveButton.innerHTML = '<i class="pi pi-bookmark text-base"></i>'
      }
    }
  }

  async saveWidgetAsEntity(component: DataInsightsComponent): Promise<void> {
    try {
      const widget = new DataInsightsWidget()
      widget.applicationId = this.currentApplicationId
      widget.dataInsightsComponent = component
      widget.created = new Date()
      widget.updated = new Date()
      console.log('widget', widget)
      const a = await this.widgetService.save(widget)
      console.log('dataaaaaaaaaaaaa', a)
    } catch (error) {
      console.error('Failed to save widget:', error)
    }
  }

}
</script>

<template>
  <div class="flex h-full">
    <!-- Dashboard Panel (Left) -->
    <div class="w-2/3 flex flex-col">
      <!-- Header -->
      <div class="p-4 border-b border-surface-200 bg-surface-50 rounded-t-lg">
        <div class="flex justify-between items-center">
          <div>
            <h2 class="text-xl font-semibold text-surface-900">Visualization Dashboard</h2>
            <p class="text-sm text-surface-600 mt-1">
              {{ visualizations.length }} visualization{{ visualizations.length !== 1 ? 's' : '' }} created
            </p>
          </div>
          
          <!-- Global Date Range Picker -->
          <div class="flex items-center gap-2">image.png
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
        </div>
      </div>

      <div class="flex-1 p-4 overflow-auto rounded-b-lg">
        <div v-if="visualizations.length === 0" class="flex items-center justify-center h-full">
          <div class="text-center text-surface-500">
            <i class="pi pi-chart-line text-4xl mb-4"></i>
            <p class="text-lg font-medium">No visualizations yet</p>
            <p class="text-sm">Start a conversation to create your first visualization</p>
          </div>
        </div>

        <div v-else id="dashboard-container" class="space-y-6">
        </div>
      </div>
    </div>

    <div class="w-1/3 border-l border-surface-200 flex flex-col">
      <div class="p-4 border-b border-surface-200 bg-surface-50 flex-shrink-0 rounded-t-lg">
        <h1 class="text-xl font-semibold text-surface-900">Data Insights Chat</h1>
        <p class="text-sm text-surface-600 mt-1">Ask questions about your data</p>
      </div>

      <div class="flex-1 p-4 overflow-y-auto min-h-0 rounded-b-lg">
        <div class="space-y-4">
          <div
            v-for="message in chatMessages"
            :key="message.id"
            class="flex"
            :class="message.type === 'user' ? 'justify-end' : 'justify-start'"
          >
            <Card
              class="max-w-xs"
              :class="message.type === 'user' ? 'bg-primary-50 border-primary-200' : 'bg-surface-50 border-surface-200'"
            >
              <template #content>
                <div class="text-sm">
                  <div class="whitespace-pre-wrap">{{ message.content }}</div>
                  
                  <div v-if="message.loading && message.tasks && message.tasks.length > 0" class="mt-3">
                    <div class="text-xs font-medium text-surface-700 mb-2">Progress:</div>
                    <ul class="space-y-1">
                      <li v-for="(task, index) in message.tasks" :key="task" class="text-xs text-surface-600 flex items-center">
                        <i v-if="index === message.tasks.length - 1" class="pi pi-spin pi-spinner mr-2 text-primary-500"></i>
                        <i v-else class="pi pi-check mr-2 text-green-500"></i>
                        {{ task }}
                      </li>
                    </ul>
                  </div>
                  <div v-if="!message.loading && message.tasks && message.tasks.length > 0" class="mt-3">
                    <button 
                      @click="message.isExpanded = !message.isExpanded"
                      class="text-xs text-primary-600 hover:text-primary-700 flex items-center"
                    >
                      <i :class="message.isExpanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" class="mr-1"></i>
                      {{ message.isExpanded ? 'Hide' : 'Show' }} task history ({{ message.tasks.length }} steps)
                    </button>
                    <div v-if="message.isExpanded" class="mt-2">
                      <ul class="space-y-1">
                        <li v-for="task in message.tasks" :key="task" class="text-xs text-surface-600 flex items-center">
                          <i class="pi pi-check mr-2 text-green-500"></i>
                          {{ task }}
                        </li>
                      </ul>
                    </div>
                  </div>
                  
                  <div class="text-xs text-surface-500 mt-2">
                    {{ message.timestamp.toLocaleTimeString() }}
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>
      </div>

      <div class="p-4 border-t border-surface-200 bg-surface-50 flex-shrink-0 rounded-b-lg">
        <div class="flex gap-2">
          <InputText
            v-model="userInput"
            placeholder="Ask about your data..."
            class="flex-1"
            :disabled="isLoading"
            @keypress="onKeyPress"
          />
          <Button
            @click="sendMessage"
            :loading="isLoading"
            :disabled="!userInput.trim() || isLoading"
            icon="pi pi-send"
            class="p-button-primary"
          />
        </div>
      </div>
    </div>
  </div>
</template> 