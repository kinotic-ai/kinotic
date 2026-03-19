import { reactive } from 'vue'

export interface InsightData {
  id: string
  title: string
  description: string
  query: string
  applicationId: string
  createdAt: Date
  data: any
  visualizationType: 'chart' | 'table' | 'stat' | 'list'
  htmlContent?: string
  metadata?: {
    tasks?: string[]
    status?: 'success' | 'error' | 'loading'
    errorMessage?: string
  }
}

export interface IInsightsState {
  insights: InsightData[]
  addInsight(insight: InsightData): void
  removeInsight(id: string): void
  getInsightsByApplication(applicationId: string): InsightData[]
  clearInsightsForApplication(applicationId: string): void
  updateInsight(id: string, updates: Partial<InsightData>): void
}

class InsightsState implements IInsightsState {
  public insights: InsightData[] = []

  addInsight(insight: InsightData): void {
    const existingIndex = this.insights.findIndex(i => i.id === insight.id)
    if (existingIndex >= 0) {
      this.insights[existingIndex] = { ...this.insights[existingIndex], ...insight }
    } else {
      this.insights.push(insight)
    }
  }

  removeInsight(id: string): void {
    const index = this.insights.findIndex(i => i.id === id)
    if (index >= 0) {
      this.insights.splice(index, 1)
    }
  }

  getInsightsByApplication(applicationId: string): InsightData[] {
    return this.insights.filter(insight => insight.applicationId === applicationId)
  }

  clearInsightsForApplication(applicationId: string): void {
    this.insights = this.insights.filter(insight => insight.applicationId !== applicationId)
  }

  updateInsight(id: string, updates: Partial<InsightData>): void {
    const index = this.insights.findIndex(i => i.id === id)
    if (index >= 0) {
      this.insights[index] = { ...this.insights[index], ...updates }
    }
  }
}

export const INSIGHTS_STATE: IInsightsState = reactive(new InsightsState())
