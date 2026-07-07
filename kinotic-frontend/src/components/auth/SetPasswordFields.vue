<template>
  <div>
    <IconField class="login-field">
      <Password
        ref="passwordInput"
        :modelValue="password"
        class="login-password"
        input-class="login-password-input"
        placeholder="Password"
        :feedback="false"
        toggleMask
        @update:modelValue="$emit('update:password', $event)"
        @keyup.enter="focusConfirm"
      />
    </IconField>

    <IconField class="login-field">
      <Password
        ref="confirmInput"
        :modelValue="confirm"
        :class="['login-password', confirmStateClass]"
        input-class="login-password-input"
        placeholder="Confirm password"
        :feedback="false"
        toggleMask
        @update:modelValue="$emit('update:confirm', $event)"
        @keyup.enter="$emit('submit')"
      />
    </IconField>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Password from 'primevue/password'
import IconField from 'primevue/iconfield'

/**
 * Password and confirm-password pair for pages that set a new password. The confirm
 * field's border tints red while the values differ and green once they match. Enter in
 * the password field focuses confirm; Enter in confirm emits {@code submit}. Bind with
 * {@code v-model:password} and {@code v-model:confirm}.
 */
defineEmits<{
  (e: 'update:password', value: string): void
  (e: 'update:confirm', value: string): void
  (e: 'submit'): void
}>()

const props = defineProps<{
  password: string
  confirm: string
}>()

const passwordInput = ref<InstanceType<typeof Password>>()
const confirmInput = ref<InstanceType<typeof Password>>()

// Stays empty (no border tint) until the user types into the confirm field.
// Once non-empty: green if it matches the password, red otherwise.
const confirmStateClass = computed<string>(() => {
  if (!props.confirm) return ''
  return props.password === props.confirm
    ? 'login-password--match'
    : 'login-password--mismatch'
})

/** Moves keyboard focus into the password field. */
function focus() {
  focusInput('passwordInput')
}

function focusConfirm() {
  focusInput('confirmInput')
}

function focusInput(refName: string) {
  const inputs: Record<string, typeof passwordInput> = { passwordInput, confirmInput }
  const el = inputs[refName]?.value as any
  if (el?.$el) {
    el.$el.querySelector('input')?.focus()
  }
}

defineExpose({ focus })
</script>

<style scoped>
/* Confirm-password border tint: red while passwords don't match, green once they do.
 * Empty state stays neutral so the user isn't yelled at before they've typed anything. */
.login-password--mismatch :deep(.login-password-input),
.login-password--match :deep(.login-password-input) {
  transition: border-color 0.18s ease;
}

.login-password--mismatch :deep(.login-password-input) {
  border-color: var(--p-red-500);
}

.login-password--match :deep(.login-password-input) {
  border-color: var(--p-green-500);
}
</style>
