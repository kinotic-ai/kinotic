<template>
  <AuthPageShell>
    <div class="login-form">
      <div class="login-form__step">
        <IconField class="login-field">
          <InputText
            ref="emailInput"
            v-model="email"
            class="login-input"
            placeholder="Email"
            @keyup.enter="handleSubmit"
          />
        </IconField>

        <IconField class="login-field">
          <Password
            v-model="password"
            input-class="login-password-input"
            class="login-password"
            placeholder="Password"
            toggleMask
            :feedback="false"
            @keyup.enter="handleSubmit"
          />
        </IconField>

        <Button
          label="Sign in"
          class="login-submit"
          :loading="loading"
          @click="handleSubmit"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { AuthPageShell, createDebug, postCredentials } from '@kinotic-ai/frontend-common'
import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const debug = createDebug('system-login')

const email = ref<string>('')
const password = ref<string>('')
const loading = ref<boolean>(false)

const emailInput = ref<any>()

const toast = useToast()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  nextTick(() => {
    const input = emailInput.value
    input?.$el?.focus?.() ?? input?.focus?.()
  })
})

async function handleSubmit() {
  if (!email.value || !password.value) {
    displayError('Please enter your email and password')
    return
  }
  loading.value = true
  try {
    await postCredentials('/api/auth/system/login', email.value, password.value)
    // Open the realtime connection, authenticated by the freshly set session cookie.
    await SYSTEM_USER_STATE.login()
    const referer = (route.query.referer as string | undefined) || '/dashboard'
    await router.push(referer)
  } catch (err) {
    debug('Login failed: %O', err)
    displayError(err instanceof Error ? err.message : 'Invalid credentials')
    password.value = ''
  } finally {
    loading.value = false
  }
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>
