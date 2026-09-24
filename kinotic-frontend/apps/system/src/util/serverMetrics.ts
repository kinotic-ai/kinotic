import { formatPercent, rateWindow, useMetricStats, type TimeRange } from '@kinotic-ai/frontend-common'

// Every deployment names the kinotic-server's OpenTelemetry agent kinotic-server (OTEL_SERVICE_NAME),
// which its JVM and HTTP metrics carry as their job in the platform's tenant
const SERVER = 'job="kinotic-server"'

const HTTP_REQUESTS = 'http_server_request_duration_seconds'

// Past these the tiles wear the red accent: a server this busy or this full has no headroom left
const HOT_CPU = 0.8
const FULL_HEAP = 0.85

/**
 * The PromQL of the kinotic-server's own metrics over the range, as its OpenTelemetry agent
 * exports them: the JVM's CPU, memory, garbage collection, threads and direct buffers, and the
 * HTTP requests it answers.
 */
export function serverQueries(range: TimeRange) {
    const window = rateWindow(range)
    const heapUsed = `sum(jvm_memory_used_bytes{${SERVER}, jvm_memory_type="heap"})`
    const heapLimit = `sum(jvm_memory_limit_bytes{${SERVER}, jvm_memory_type="heap"})`
    const requests = `sum(rate(${HTTP_REQUESTS}_count{${SERVER}}[${window}]))`
    return {
        cpuByServer: `max by (instance) (jvm_cpu_recent_utilization_ratio{${SERVER}})`,
        busiestCpu: `max(jvm_cpu_recent_utilization_ratio{${SERVER}})`,
        heapUsed,
        heapLimit,
        heapShare: `${heapUsed} / ${heapLimit}`,
        nonHeapUsed: `sum(jvm_memory_used_bytes{${SERVER}, jvm_memory_type="non_heap"})`,
        gcTime: `sum by (jvm_gc_name) (rate(jvm_gc_duration_seconds_sum{${SERVER}}[${window}]))`,
        threads: `sum(jvm_thread_count{${SERVER}})`,
        blockedThreads: `sum(jvm_thread_count{${SERVER}, jvm_thread_state="blocked"})`,
        directBuffers: `sum by (jvm_buffer_pool_name) (jvm_buffer_memory_used_bytes{${SERVER}})`,
        httpRequests: requests,
        httpServerErrors: `(sum(rate(${HTTP_REQUESTS}_count{${SERVER}, http_response_status_code=~"5.."}[${window}])) or vector(0)) / ${requests}`,
        httpLatencyP95: `histogram_quantile(0.95, sum by (le) (rate(${HTTP_REQUESTS}_bucket{${SERVER}}[${window}])))`
    }
}

/**
 * The servers' headroom over the last hour as dashboard statistics: the CPU of the busiest
 * server process, and the share of the servers' heap in use. Each leads to the Cluster page.
 */
export function useServerStats() {
    return useMetricStats(() => null, range => {
        const queries = serverQueries(range)
        return [
            {
                label: 'CPU',
                description: 'busiest server',
                query: queries.busiestCpu,
                format: formatPercent,
                icon: 'pi-microchip',
                accent: latest => latest >= HOT_CPU ? 'red' : 'teal',
                to: '/cluster'
            },
            {
                label: 'Heap',
                description: 'in use',
                query: queries.heapShare,
                format: formatPercent,
                icon: 'pi-database',
                accent: latest => latest >= FULL_HEAP ? 'red' : 'amber',
                to: '/cluster'
            }
        ]
    })
}
