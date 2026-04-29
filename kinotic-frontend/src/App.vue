<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import '@/composables/useTheme'
import { StructuresStates } from '@/states'

/**
 * Picks up the Kinotic JWT delivered as a URL fragment (`#token=<jwt>`) by
 * `/api/login/callback` and `/api/signup/complete-org`, exchanges it for a STOMP
 * session, then strips the fragment and forwards to the default route.
 *
 * The fragment never reaches the server (browsers don't send fragments on requests),
 * so the token never appears in access logs.
 */
const router = useRouter()
const toast = useToast()
const userState = StructuresStates.getUserState()

async function consumeTokenFragment() {
  const hash = window.location.hash
  if (!hash || !hash.includes('token=')) return

  const params = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash)
  const token = params.get('token')
  if (!token) return

  // Strip the token fragment from the URL whether or not the login succeeds.
  history.replaceState(null, '', window.location.pathname + window.location.search)

  try {
    await userState.loginWithToken(token)
    await router.replace('/applications')
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Token sign-in failed'
    toast.add({ severity: 'error', summary: 'Sign-in failed', detail: message, life: 8000 })
    await router.replace('/login')
  }
}

onMounted(consumeTokenFragment)
</script>

<template>
    <main>
        <Toast />
        <router-view />
    </main>
</template>

<style scoped>
</style>
