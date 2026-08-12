<template>
  <div>
    <h1 class="text-[1.4rem] font-semibold mb-5">Overview</h1>

    <div class="grid grid-cols-[repeat(auto-fit,minmax(24rem,1fr))] gap-5 items-start">
      <ClusterInfoCard :info="clusterInfo" :error="clusterError" />
      <PlatformStatsCard />
      <LogLevelCard :node-ids="nodeIds" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import type { KinoticClusterInfo } from '@kinotic-ai/os-api'

import ClusterInfoCard from '@/components/ClusterInfoCard.vue'
import LogLevelCard from '@/components/LogLevelCard.vue'
import PlatformStatsCard from '@/components/PlatformStatsCard.vue'

// The cluster query is made once here; the cluster card renders it and the
// log-level card offers its node ids as targets.
const clusterInfo = ref<KinoticClusterInfo | null>(null)
const clusterError = ref<string | null>(null)

const nodeIds = computed(() => clusterInfo.value?.nodes.map(node => node.nodeId) ?? [])

onMounted(async () => {
  try {
    clusterInfo.value = await Kinotic.clusterInfo.getClusterInfo()
  } catch (err) {
    clusterError.value = err instanceof Error ? err.message : 'Failed to load cluster info'
  }
})
</script>
