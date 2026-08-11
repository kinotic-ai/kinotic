<template>
  <Card>
    <template #title>Platform</template>
    <template #content>
      <div v-if="error" class="card-error">
        <Message severity="error" :closable="false">{{ error }}</Message>
      </div>
      <div v-else class="platform-stats">
        <div class="platform-stat">
          <span class="platform-stat__value">{{ organizationCount ?? '—' }}</span>
          <span class="platform-stat__label">Organizations</span>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Card from 'primevue/card'
import Message from 'primevue/message'

import { Kinotic } from '@kinotic-ai/core'

const organizationCount = ref<number | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    organizationCount.value = await Kinotic.organizations.count()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load platform stats'
  }
})
</script>

<style scoped>
.platform-stats {
  display: flex;
  gap: 2rem;
}

.platform-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.platform-stat__value {
  font-size: 1.5rem;
  font-weight: 600;
}

.platform-stat__label {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
}
</style>
