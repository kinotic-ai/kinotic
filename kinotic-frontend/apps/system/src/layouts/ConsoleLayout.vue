<template>
  <div class="console">
    <header class="console__header">
      <span class="console__brand">Kinotic System Console</span>
      <div class="console__header-right">
        <span class="console__participant">{{ participantId }}</span>
        <Button label="Sign out" severity="secondary" text size="small" :loading="signingOut" @click="handleLogout" />
      </div>
    </header>

    <div class="console__body">
      <nav class="console__sidebar">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="console__nav-item"
          active-class="console__nav-item--active"
        >
          <span :class="['pi', item.icon, 'console__nav-icon']" aria-hidden="true"></span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <main class="console__main">
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
  { to: '/service-directory', label: 'Service directory', icon: 'pi-sitemap' },
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

<style scoped>
.console {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.console__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid var(--p-content-border-color);
}

.console__brand {
  font-weight: 600;
}

.console__header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.console__participant {
  font-size: 0.85rem;
  color: var(--p-text-muted-color);
}

.console__body {
  display: flex;
  flex: 1;
}

.console__sidebar {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  width: 13rem;
  padding: 1rem 0.5rem;
  border-right: 1px solid var(--p-content-border-color);
}

.console__nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  color: var(--p-text-color);
  text-decoration: none;
}

.console__nav-item:hover {
  background: var(--p-content-hover-background);
}

.console__nav-item--active {
  background: var(--p-highlight-background);
  color: var(--p-highlight-color);
}

.console__nav-icon {
  font-size: 0.9rem;
}

.console__main {
  flex: 1;
  padding: 1.5rem;
}
</style>
