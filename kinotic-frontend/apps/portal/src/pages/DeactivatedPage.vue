<template>
  <AuthPageShell>
    <div class="deactivated">
      <CircleOff :size="40" :stroke-width="1.5" class="deactivated__icon" aria-hidden="true" />
      <h1 class="deactivated__title">{{ organizationId }} is deactivated</h1>
      <p class="deactivated__text">
        This organization's sites and services are offline because its Kinotic Cloud payment is overdue.
        They come back as soon as the payment is made.
      </p>
      <p class="deactivated__text">
        If you are a member of {{ organizationId }}, sign in and open Organization settings, Billing, to pay.
      </p>
      <RouterLink :to="`/login/${encodeURIComponent(organizationId)}`" class="deactivated__link">
        Sign in to {{ organizationId }}
      </RouterLink>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { CircleOff } from '@lucide/vue'
import { AuthPageShell } from '@kinotic-ai/frontend-common'

defineProps<{
  organizationId: string
}>()
</script>

<style scoped>
.deactivated {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  text-align: center;
}

.deactivated__icon {
  color: var(--lp-text);
}

.deactivated__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.deactivated__text {
  margin: 0;
  color: var(--lp-text);
}

.deactivated__link {
  font-weight: 600;
  text-decoration: underline;
}
</style>
