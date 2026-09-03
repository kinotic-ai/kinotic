import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'

/**
 * The ECharts modules the telemetry charts render with. ECharts is consumed tree-shaken, so
 * every chart type and component they use is registered here, once, before a VChart mounts.
 */
use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])
