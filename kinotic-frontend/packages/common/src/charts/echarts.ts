import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'

/**
 * Registers the ECharts modules this package's charts render with. ECharts is consumed tree-shaken, so
 * every chart type and component a chart uses must be registered before its VChart mounts. Idempotent.
 */
export function registerChartModules(): void {
    use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])
}
