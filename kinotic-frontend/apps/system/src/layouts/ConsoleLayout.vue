<template>
  <div class="flex flex-col min-h-screen">
    <header class="flex items-center justify-between px-5 py-3 border-b border-surface">
      <span class="font-semibold">Kinotic System Console</span>
      <div class="flex items-center gap-3">
        <span class="text-sm text-muted-color">{{ participantId }}</span>
        <Button label="Sign out" severity="secondary" text size="small" :loading="signingOut" @click="handleLogout" />
      </div>
    </header>

    <div class="flex flex-1">
      <nav class="flex flex-col gap-1 w-52 px-2 py-4 border-r border-surface">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-2 px-3 py-2 rounded-md text-color no-underline hover:bg-emphasis"
          active-class="bg-highlight"
        >
          <span :class="['pi', item.icon, 'text-sm']" aria-hidden="true"></span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <main class="flex-1 p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const navItems = [
  { to: '/overview', label: 'Overview', icon: 'pi-objects-column' },
  { to: '/organizations', label: 'Organizations', icon: 'pi-building' },
  { to: '/nodes', label: 'Nodes & workloads', icon: 'pi-server' },
]

const router = useRouter()
const signingOut = ref(false)

const participantId = computed(() => SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? '')

async function handleLogout() {
  signingOut.value = true
  try {
    await SYSTEM_USER_STATE.logout()
  } finally {
    signingOut.value = false
    await router.push('/login')
  }
}
</script>
