<template>
  <Card>
    <template #title>Cluster</template>
    <template #content>
      <div v-if="error" class="card-error">
        <Message severity="error" :closable="false">{{ error }}</Message>
      </div>
      <div v-else-if="!info" class="card-loading">
        <ProgressSpinner class="card-spinner" strokeWidth="6" />
      </div>
      <div v-else>
        <div class="cluster-stats">
          <div class="cluster-stat">
            <span class="cluster-stat__value">{{ info.serverNodeCount }}</span>
            <span class="cluster-stat__label">Server nodes</span>
          </div>
          <div class="cluster-stat">
            <span class="cluster-stat__value">{{ info.topologyVersion }}</span>
            <span class="cluster-stat__label">Topology version</span>
          </div>
          <div class="cluster-stat">
            <Tag :severity="info.active ? 'success' : 'danger'" :value="info.clusterState" />
            <span class="cluster-stat__label">State</span>
          </div>
        </div>

        <table class="node-table">
          <thead>
            <tr>
              <th>Node</th>
              <th>Version</th>
              <th>Order</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in info.nodes" :key="node.nodeId">
              <td class="node-table__id">{{ node.nodeId }}</td>
              <td>{{ node.version }}</td>
              <td>{{ node.order }}</td>
              <td><Tag v-if="node.local" severity="info" value="serving request" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import Card from 'primevue/card'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'

import type { KinoticClusterInfo } from '@/domain/ClusterInfo'

defineProps<{
  info: KinoticClusterInfo | null
  error: string | null
}>()
</script>

<style scoped>
.card-loading {
  display: flex;
  justify-content: center;
  padding: 1rem;
}

.card-spinner {
  width: 2rem;
  height: 2rem;
}

.cluster-stats {
  display: flex;
  gap: 2rem;
  margin-bottom: 1rem;
}

.cluster-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.cluster-stat__value {
  font-size: 1.5rem;
  font-weight: 600;
}

.cluster-stat__label {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
}

.node-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.node-table th {
  text-align: left;
  padding: 0.4rem 0.5rem;
  color: var(--p-text-muted-color);
  font-weight: 500;
  border-bottom: 1px solid var(--p-content-border-color);
}

.node-table td {
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid var(--p-content-border-color);
}

.node-table__id {
  font-family: monospace;
  font-size: 0.8rem;
}
</style>
