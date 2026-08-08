<template>
  <div class="overview">
    <header class="overview__header">
      <h1 class="overview__title">Kinotic System Console</h1>
      <Button label="Sign out" severity="secondary" outlined :loading="loading" @click="handleLogout" />
    </header>

    <p class="overview__subtitle">
      Signed in as <strong>{{ participantId }}</strong>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const router = useRouter()
const loading = ref(false)

const participantId = computed(() => SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? 'unknown')

async function handleLogout() {
  loading.value = true
  try {
    await SYSTEM_USER_STATE.logout()
  } finally {
    loading.value = false
    await router.push('/login')
  }
}
</script>

<style scoped>
.overview {
  padding: 2rem;
}

.overview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.overview__title {
  font-size: 1.5rem;
  font-weight: 600;
}

.overview__subtitle {
  margin-top: 0.75rem;
  color: var(--p-text-muted-color);
}
</style>
