<template>
  <form :action="action" method="post" class="social-auth-button-form">
    <button type="submit" class="social-auth-button">
      <span v-if="logo && monochrome"
            class="social-auth-button__logo social-auth-button__logo--mark"
            :style="markStyle"
            aria-hidden="true"></span>
      <img v-else-if="logo" :src="logo" alt="" class="social-auth-button__logo" aria-hidden="true" />
      <span class="social-auth-button__label">{{ label }}</span>
    </button>
  </form>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { type OidcProviderType } from '@kinotic-ai/management-api'

import { PROVIDER_LOGOS, providerDisplayName } from '@/util/oidcConfigurations'

/** What the button does: signs in, signs up, or connects the provider to the signed-in user. */
export type Intent = 'sign-in' | 'sign-up' | 'connect'

const props = defineProps<{
  provider: string
  action: string
  intent: Intent
  /** The configuration's own name, shown in place of the provider's for a provider other than Google, Microsoft or GitHub, such as an organization's Okta. */
  name?: string
}>()

// marks drawn in the button's text color, so they read on both the light and the dark surface
const MONOCHROME_PROVIDERS = new Set(['github', 'okta'])

// Google's and Microsoft's branding fix the button text to their own name, and GitHub's button reads the same way
const BRAND_NAMED_PROVIDERS = new Set(['google', 'azure-ad', 'github'])

const logo = computed<string | undefined>(() => PROVIDER_LOGOS[props.provider as OidcProviderType])

const monochrome = computed<boolean>(() => MONOCHROME_PROVIDERS.has(props.provider))

// quoted, since Vite inlines a small SVG as a data URL whose single quotes an unquoted url() rejects
const markStyle = computed(() => ({ '--social-auth-button-mark': `url("${logo.value}")` }))

/** The verb the intent reads as before the provider's name. */
const verb = computed<string>(() => {
  switch (props.intent) {
    case 'sign-up': return 'Sign up with'
    case 'connect': return 'Connect'
    default:        return 'Sign in with'
  }
})

const label = computed<string>(() => {
  const shown = BRAND_NAMED_PROVIDERS.has(props.provider) ? providerDisplayName(props.provider) : props.name ?? providerDisplayName(props.provider)
  return `${verb.value} ${shown}`
})
</script>

<style scoped>
.social-auth-button-form {
  margin: 0;
  width: 100%;
}

/* Every provider gets the same box, as tall as the auth pages' inputs. The auth pages' provider
 * tokens color it there; the content tokens color it on any other page. */
.social-auth-button {
  display: flex;
  width: 100%;
  height: 3.5rem;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 0 1rem;
  border: 1px solid var(--lp-provider-border, var(--p-content-border-color));
  border-radius: 5px;
  background: var(--lp-provider-bg, var(--p-content-background));
  color: var(--lp-provider-color, var(--p-text-color));
  font: inherit;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.social-auth-button:hover {
  border-color: var(--lp-provider-border-hover, var(--p-content-border-color));
  background: var(--lp-provider-bg-hover, var(--p-content-hover-background));
}

.social-auth-button:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}

.social-auth-button__logo {
  width: 1.25rem;
  height: 1.25rem;
  flex: 0 0 auto;
}

/* A monochrome mark is a CSS mask filled with the text color, so one asset serves both modes. */
.social-auth-button__logo--mark {
  background: currentColor;
  mask: var(--social-auth-button-mark) center / contain no-repeat;
  -webkit-mask: var(--social-auth-button-mark) center / contain no-repeat;
}

.social-auth-button__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
