<script setup lang="ts">
import { ref } from 'vue'
import { login, session } from '../session'

const email = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)

async function submit(): Promise<void> {
    error.value = null
    if (!email.value || !password.value) {
        error.value = 'Enter your email and password'
        return
    }
    busy.value = true
    try {
        await login(email.value, password.value)
    } catch (reason) {
        error.value = reason instanceof Error ? reason.message : 'Sign-in failed'
        password.value = ''
    } finally {
        busy.value = false
    }
}
</script>

<template>
  <section class="login">
    <form class="panel login__card" @submit.prevent="submit">
      <p class="label">Kinotic OS</p>
      <h1 class="login__title neon-cyan pulse">Org Dashboard</h1>
      <p class="login__lead">Sign in as a member of your organization.</p>

      <p v-if="session.ended" class="alert">{{ session.ended }}</p>

      <label class="login__field">
        <span class="label">Email</span>
        <input v-model="email" class="input" type="email" autocomplete="username" placeholder="you@example.com" autofocus />
      </label>

      <label class="login__field">
        <span class="label">Password</span>
        <input v-model="password" class="input" type="password" autocomplete="current-password" placeholder="••••••••" />
      </label>

      <p v-if="error" class="alert">{{ error }}</p>

      <button class="button login__submit" type="submit" :disabled="busy">
        {{ busy ? 'Signing in' : 'Sign in' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.login {
  min-height: 80vh;
  display: grid;
  place-items: center;
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: 32px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border-color: rgba(25, 240, 255, 0.35);
  box-shadow: 0 0 40px rgba(25, 240, 255, 0.18), 0 0 80px rgba(255, 43, 214, 0.12);
}

.login__title {
  font-size: 26px;
}

.login__lead {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.login__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.login__submit {
  margin-top: 8px;
  padding: 12px 18px;
}
</style>
