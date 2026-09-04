import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'

/**
 * The ECharts modules the apps render with. ECharts is consumed tree-shaken, so every chart
 * type and component a chart uses is registered here, once, before any VChart mounts.
 */
use([CanvasRenderer, BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent])
