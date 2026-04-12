<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Card from 'primevue/card'
import ScrollPanel from 'primevue/scrollpanel'
import Divider from 'primevue/divider'
import Calendar from 'primevue/calendar'
import { Kinotic } from '@kinotic-ai/core'
import { ProgressType } from '@kinotic-ai/os-api'
import type { InsightRequest, DataInsightsComponent, InsightProgress } from '@kinotic-ai/os-api'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { INSIGHTS_STATE, type InsightData } from '@/states/IInsightsState'
import { DataInsightsWidgetEntityRepository } from '@/services/DataInsightsWidgetEntityRepository'
import { DataInsightsWidget } from '@/domain/DataInsightsWidget'
import { createDebug } from '@/util/debug'
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('data-insights');

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
    // @ts-ignore
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
  renderedVisualizationIds: Set<string> = new Set()
  currentApplicationId: string = ''
  dateRange: DateRange = {
    startDate: null,
    endDate: null
  }
  showDateRangePicker: boolean = false
  widgetService: DataInsightsWidgetEntityRepository = new DataInsightsWidgetEntityRepository()

  get isDark() {
    return darkMode.value
  }

  get currentApplicationName(): string {
    const appId = APPLICATION_STATE.currentApplication?.id
    return appId || 'Unknown Application'
  }

  mounted() {
    if (!window.globalDateRangeObservable) {
      // @ts-ignore
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
      this.renderedVisualizationIds = new Set()
      const dashboardContainer = document.getElementById('dashboard-container')
      if (dashboardContainer) {
        dashboardContainer.innerHTML = ''
      }
      this.addWelcomeMessage()
    }
  }

  restoreStateFromStoredInsights(): void {
    const storedInsights = INSIGHTS_STATE.getInsightsByApplication(this.currentApplicationId)
    
    if (storedInsights.length === 0) {
      this.visualizations = []
      this.chatMessages = []
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
      this.renderedVisualizationIds = new Set()
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
      const insightsService = Kinotic.dataInsights
      
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
             debug('Received %d components', progress.components.length)
             progress.components.forEach(async (component: DataInsightsComponent) => {
               debug('Processing component: %s (%s)', component.id, component.name)
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

               debug('Executing visualization for: %s', component.id)
               this.executeVisualization(component.rawHtml, component.id)
             })
             debug('Total visualizations: %d', this.visualizations.length)
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
        error: (error: Error) => {
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
    debug('Executing visualization for: %s', componentId)
    
    if (componentId && this.renderedVisualizationIds.has(componentId)) {
      debug('Visualization already rendered, skipping: %s', componentId)
      return
    }
    
    if (componentId) {
      this.renderedVisualizationIds.add(componentId)
      debug('Marked as rendering: %s', componentId)
    }
    
    try {
      const script = document.createElement('script')
      script.textContent = htmlContent
      document.head.appendChild(script)
      debug('Script added to head')
      
      const elementNameMatch = htmlContent.match(/customElements\.define\(['"`]([^'"`]+)['"`]/)
      const elementName = elementNameMatch ? elementNameMatch[1] : 'data-insights-dashboard'
      debug('Custom element name: %s', elementName)
      
      setTimeout(() => {
        try {
          debug('Checking if custom element is registered: %s', elementName)
          if (!customElements.get(elementName)) {
            debug('Custom element not registered: %s', elementName)
            return
          }
          debug('Custom element is registered: %s', elementName)
          
          const wrapper = document.createElement('div')
          wrapper.className = 'visualization-wrapper relative'
          wrapper.setAttribute('data-viz-id', componentId || '')
          
          const saveButton = document.createElement('button')
          saveButton.className = this.isDark
            ? 'save-widget-btn absolute top-2 right-2 bg-[#171717] hover:bg-primary-500 hover:text-white text-[#d4d4d8] border border-[#2f2f35] rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
            : 'save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
          saveButton.innerHTML = '<i class="pi pi-bookmark text-base"></i>'
          saveButton.onclick = () => this.handleSaveWidget(componentId!)
          
          const element = document.createElement(elementName)
          debug('Created element: %s', elementName)
          
          wrapper.appendChild(saveButton)
          wrapper.appendChild(element)
          
          const dashboardContainer = document.getElementById('dashboard-container')
          debug('Dashboard container found: %s', !!dashboardContainer)
          if (dashboardContainer) {
            dashboardContainer.appendChild(wrapper)
            debug('Visualization added to dashboard container')
          } else {
            debug('Dashboard container not found!')
          }
        } catch (error) {
          debug('Error in setTimeout block: %O', error)
        }
      }, 1000)
    } catch (error) {
      debug('Error in executeVisualization: %O', error)
    }
  }

  onKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      this.sendMessage()
    }
  }

  generateInsightTitle(query: string): string {
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
      const wrapper = document.querySelector(`[data-viz-id="${componentId}"]`)
      const saveButton = wrapper?.querySelector('.save-widget-btn') as HTMLButtonElement
      if (saveButton) {
        saveButton.disabled = true
        saveButton.innerHTML = '<i class="pi pi-spin pi-spinner text-base"></i>'
      }
      
      await this.saveWidgetAsEntity(visualization.component)
      
      visualization.saved = true
      
      if (saveButton) {
        saveButton.className = this.isDark
          ? 'save-widget-btn absolute top-2 right-2 bg-[#262626] text-[#8d8d96] rounded p-2 shadow-sm border border-[#2f2f35] z-10 flex items-center justify-center w-10 h-10 cursor-default'
          : 'save-widget-btn absolute top-2 right-2 bg-gray-200 text-gray-600 rounded p-2 shadow-sm z-10 flex items-center justify-center w-10 h-10 cursor-default'
        saveButton.innerHTML = '<i class="pi pi-bookmark-fill text-base"></i>'
        saveButton.disabled = true
      }
      
    } catch (error) {
      const wrapper = document.querySelector(`[data-viz-id="${componentId}"]`)
      const saveButton = wrapper?.querySelector('.save-widget-btn') as HTMLButtonElement
      if (saveButton) {
        saveButton.disabled = false
        saveButton.className = this.isDark
          ? 'save-widget-btn absolute top-2 right-2 bg-[#171717] hover:bg-primary-500 hover:text-white text-[#d4d4d8] border border-[#2f2f35] rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
          : 'save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10'
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
      await this.widgetService.save(widget)
    } catch (error) {
    }
  }

}
</script>

<template>
  <div :class="['flex h-full transition-colors', isDark ? 'bg-[#171717] text-white' : 'bg-transparent text-[#101010]']">
    <!-- Dashboard Panel (Left) -->
    <div class="w-2/3 flex flex-col">
      <!-- Header -->
      <div :class="['rounded-t-lg border-b p-4', isDark ? 'border-[#2f2f35] bg-[#171717]' : 'border-surface-200 bg-surface-50']">
        <div class="flex justify-between items-center">
          <div>
            <h2 :class="['text-xl font-semibold', isDark ? 'text-white' : 'text-surface-900']">Visualization Dashboard</h2>
            <p :class="['mt-1 text-sm', isDark ? 'text-[#9f9fa8]' : 'text-surface-600']">
              {{ visualizations.length }} visualization{{ visualizations.length !== 1 ? 's' : '' }} created
            </p>
          </div>
          
          <!-- Global Date Range Picker -->
          <div class="flex items-center gap-2">
            <Button
              @click="toggleDateRangePicker"
              :class="showDateRangePicker ? 'p-button-primary' : 'p-button-outlined'"
              icon="pi pi-calendar"
              size="small"
              :label="showDateRangePicker ? 'Hide Date Range' : 'Set Date Range'"
            />
            
            <div v-if="showDateRangePicker" :class="['flex items-center gap-2 rounded border p-2', isDark ? 'border-[#3a3a40] bg-[#171717]' : 'bg-white']">
              <div class="flex items-center gap-2">
                <label :class="['text-sm font-medium', isDark ? 'text-[#d4d4d8]' : 'text-surface-700']">From:</label>
                <Calendar
                  v-model="dateRange.startDate"
                  @date-select="updateDateRange"
                  placeholder="Start Date"
                  size="small"
                  showIcon
                />
              </div>
              
              <div class="flex items-center gap-2">
                <label :class="['text-sm font-medium', isDark ? 'text-[#d4d4d8]' : 'text-surface-700']">To:</label>
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

      <div :class="['flex-1 overflow-auto rounded-b-lg p-4', isDark ? 'bg-[#171717]' : 'bg-transparent']">
        <div v-if="visualizations.length === 0" class="flex items-center justify-center h-full">
          <div :class="['text-center', isDark ? 'text-[#9f9fa8]' : 'text-surface-500']">
            <i class="pi pi-chart-line text-4xl mb-4"></i>
            <p class="text-lg font-medium">No visualizations yet</p>
            <p class="text-sm">Start a conversation to create your first visualization</p>
          </div>
        </div>

        <div v-else id="dashboard-container" class="space-y-6">
        </div>
      </div>
    </div>

    <div :class="['flex w-1/3 flex-col border-l', isDark ? 'border-[#2f2f35]' : 'border-surface-200']">
      <div :class="['flex-shrink-0 rounded-t-lg border-b p-4', isDark ? 'border-[#2f2f35] bg-[#171717]' : 'border-surface-200 bg-surface-50']">
        <h1 :class="['text-xl font-semibold', isDark ? 'text-white' : 'text-surface-900']">Data Insights Chat</h1>
        <p :class="['mt-1 text-sm', isDark ? 'text-[#9f9fa8]' : 'text-surface-600']">Ask questions about your data</p>
      </div>

      <div :class="['min-h-0 flex-1 overflow-y-auto rounded-b-lg p-4', isDark ? 'bg-[#171717]' : 'bg-transparent']">
        <div class="space-y-4">
          <div
            v-for="message in chatMessages"
            :key="message.id"
            class="flex"
            :class="message.type === 'user' ? 'justify-end' : 'justify-start'"
          >
            <Card
              class="max-w-xs"
              :class="message.type === 'user' ? (isDark ? 'bg-[#2b1a20] border-[#5b2433]' : 'bg-primary-50 border-primary-200') : (isDark ? 'bg-[#202024] border-[#2f2f35]' : 'bg-surface-50 border-surface-200')"
            >
              <template #content>
                <div class="text-sm">
                  <div class="whitespace-pre-wrap">{{ message.content }}</div>
                  
                  <div v-if="message.loading && message.tasks && message.tasks.length > 0" class="mt-3">
                    <div :class="['mb-2 text-xs font-medium', isDark ? 'text-[#d4d4d8]' : 'text-surface-700']">Progress:</div>
                    <ul class="space-y-1">
                      <li v-for="(task, index) in message.tasks" :key="task" :class="['flex items-center text-xs', isDark ? 'text-[#a1a1aa]' : 'text-surface-600']">
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
                        <li v-for="task in message.tasks" :key="task" :class="['flex items-center text-xs', isDark ? 'text-[#a1a1aa]' : 'text-surface-600']">
                          <i class="pi pi-check mr-2 text-green-500"></i>
                          {{ task }}
                        </li>
                      </ul>
                    </div>
                  </div>
                  
                  <div :class="['mt-2 text-xs', isDark ? 'text-[#7f7f86]' : 'text-surface-500']">
                    {{ message.timestamp.toLocaleTimeString() }}
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>
      </div>

      <div :class="['flex-shrink-0 rounded-b-lg border-t p-4', isDark ? 'border-[#2f2f35] bg-[#171717]' : 'border-surface-200 bg-surface-50']">
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