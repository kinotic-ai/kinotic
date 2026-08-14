<template>
  <Card>
    <template #title>Cluster</template>
    <template #content>
      <div v-if="error">
        <Message severity="error" :closable="false">{{ error }}</Message>
      </div>
      <div v-else-if="!info" class="flex justify-center p-4">
        <ProgressSpinner class="!w-8 !h-8" strokeWidth="6" />
      </div>
      <div v-else>
        <div class="flex gap-8 mb-4">
          <div class="flex flex-col gap-1">
            <span class="text-2xl font-semibold">{{ info.serverNodeCount }}</span>
            <span class="text-xs text-muted-color">Server nodes</span>
          </div>
          <div class="flex flex-col gap-1">
            <span class="text-2xl font-semibold">{{ info.topologyVersion }}</span>
            <span class="text-xs text-muted-color">Topology version</span>
          </div>
          <div class="flex flex-col gap-1">
            <Tag :severity="info.active ? 'success' : 'danger'" :value="info.clusterState" />
            <span class="text-xs text-muted-color">State</span>
          </div>
        </div>

        <div class="overflow-x-auto">
        <table class="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th class="text-left px-2 py-1.5 text-muted-color font-medium border-b border-surface">Node</th>
              <th class="text-left px-2 py-1.5 text-muted-color font-medium border-b border-surface">Version</th>
              <th class="text-left px-2 py-1.5 text-muted-color font-medium border-b border-surface">Order</th>
              <th class="border-b border-surface"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in info.nodes" :key="node.nodeId">
              <td class="px-2 py-1.5 border-b border-surface font-mono text-xs">{{ node.nodeId }}</td>
              <td class="px-2 py-1.5 border-b border-surface">{{ node.version }}</td>
              <td class="px-2 py-1.5 border-b border-surface">{{ node.order }}</td>
              <td class="px-2 py-1.5 border-b border-surface"><Tag v-if="node.local" severity="info" value="serving request" /></td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import Card from 'primevue/card'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'

import type { KinoticClusterInfo } from '@kinotic-ai/os-api'

defineProps<{
  info: KinoticClusterInfo | null
  error: string | null
}>()
</script>
