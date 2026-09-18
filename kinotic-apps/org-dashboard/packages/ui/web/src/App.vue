<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DashboardView from './components/DashboardView.vue'
import LoginView from './components/LoginView.vue'
import { restore, session } from './session'

/** True while the session an earlier visit left is being checked, before either view paints. */
const restoring = ref(true)

onMounted(async () => {
    try {
        await restore()
    } catch {
        // an unreachable server or a rejected connection leaves the login view to say so on the next attempt
    } finally {
        restoring.value = false
    }
})
</script>

<template>
  <main class="app">
    <div v-if="restoring" class="boot">
      <span class="label neon-cyan pulse">connecting</span>
    </div>
    <DashboardView v-else-if="session.participant" :participant="session.participant" />
    <LoginView v-else />
  </main>
</template>

<style scoped>
.boot {
  min-height: 60vh;
  display: grid;
  place-items: center;
}
</style>
