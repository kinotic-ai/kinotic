import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'

/**
 * The ECharts modules this app renders with. ECharts is consumed tree-shaken: every chart
 * type and component a page uses must be registered here, once, before a VChart mounts.
 */
use([CanvasRenderer, BarChart, GridComponent, LegendComponent, TooltipComponent])
