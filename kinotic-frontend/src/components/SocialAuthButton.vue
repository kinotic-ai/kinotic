<template>
  <form :action="action" method="post" class="social-auth-button-form">
    <!-- Google: official pre-rendered web pill SVG (logo + label baked in). -->
    <button v-if="brandedAsset" type="submit" class="social-auth-button social-auth-button--branded" :aria-label="ariaLabel">
      <img :src="brandedAsset" :alt="ariaLabel" class="social-auth-button__asset" />
    </button>
    <!-- Microsoft: composed per the Entra "Add a Microsoft branded button" guidance —
         logo SVG + literal "Sign in with Microsoft" text in light/dark variants. -->
    <button v-else-if="provider === 'azure-ad'"
            type="submit"
            :class="['social-auth-button', 'social-auth-button--ms', isDarkMode ? 'social-auth-button--ms-dark' : 'social-auth-button--ms-light']"
            :aria-label="microsoftLabel">
      <img :src="microsoftLogo" alt="" class="social-auth-button__ms-logo" aria-hidden="true" />
      <span class="social-auth-button__ms-label">{{ microsoftLabel }}</span>
    </button>
    <!-- Fallback for any other provider until we have its branded artwork. -->
    <button v-else type="submit" class="social-auth-button social-auth-button--generic">
      <span class="social-auth-button__label">{{ genericLabel }}</span>
    </button>
  </form>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'
import { isDark } from '@/composables/useTheme'

import googleSignInLight from '@/assets/social/google/web_light_rd_SI.svg'
import googleSignInDark  from '@/assets/social/google/web_dark_rd_SI.svg'
import googleSignUpLight from '@/assets/social/google/web_light_rd_SU.svg'
import googleSignUpDark  from '@/assets/social/google/web_dark_rd_SU.svg'
import microsoftLogoUrl  from '@/assets/social/entra/ms-symbollockup_mssymbol_19.svg'

export type Intent = 'sign-in' | 'sign-up'

@Component
export default class SocialAuthButton extends Vue {
  @Prop({ required: true }) provider!: string
  @Prop({ required: true }) action!: string
  @Prop({ required: true }) intent!: Intent

  get isDarkMode(): boolean {
    return isDark.value
  }

  get brandedAsset(): string | null {
    if (this.provider === 'google') {
      const dark = isDark.value
      if (this.intent === 'sign-up') return dark ? googleSignUpDark : googleSignUpLight
      return dark ? googleSignInDark : googleSignInLight
    }
    return null
  }

  get microsoftLogo(): string {
    return microsoftLogoUrl
  }

  get microsoftLabel(): string {
    return this.intent === 'sign-up' ? 'Sign up with Microsoft' : 'Sign in with Microsoft'
  }

  get providerDisplayName(): string {
    switch (this.provider) {
      case 'azure-ad': return 'Microsoft'
      case 'google':   return 'Google'
      default:         return this.provider.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
    }
  }

  get genericLabel(): string {
    return `${this.intent === 'sign-up' ? 'Sign up' : 'Sign in'} with ${this.providerDisplayName}`
  }

  get ariaLabel(): string {
    return this.genericLabel
  }
}
</script>

<style scoped>
.social-auth-button-form {
  margin: 0;
  display: flex;
  justify-content: center;
}

.social-auth-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 0;
  padding: 0;
  background: transparent;
}

/* Branded buttons render the IdP's official artwork — preserve aspect ratio,
 * never repaint, never resize the logo, never change the text. Google's web pill
 * is 179×40 native; we match that height so it lines up with the Microsoft button
 * (41px per Entra spec). Width is whatever the SVG's aspect ratio dictates. */
.social-auth-button--branded {
  width: auto;
}

.social-auth-button--branded:focus-visible {
  outline: 2px solid var(--p-primary-color);
  outline-offset: 2px;
  border-radius: 9999px;
}

.social-auth-button__asset {
  display: block;
  height: 40px;
  width: auto;
}

/* Microsoft button — composed per the Entra "Add a Microsoft branded button" specs:
 * 41px tall, 21x21 logo with 12px padding from the left edge, 12px gap to the text,
 * 12px right padding, Segoe UI Regular 15px, light/dark color variants. We don't
 * modify the logo itself (rendered as <img>) and don't repaint the button colors. */
.social-auth-button--ms {
  display: inline-flex;
  align-items: center;
  height: 41px;
  width: auto;
  padding: 0 12px;
  font-family: "Segoe UI", system-ui, sans-serif;
  font-size: 15px;
  font-weight: 400;
  line-height: 1;
  border-radius: 0;
}

.social-auth-button--ms-light {
  background: #FFFFFF;
  border: 1px solid #8C8C8C;
  color: #5E5E5E;
}

.social-auth-button--ms-dark {
  background: #2F2F2F;
  border: 1px solid #2F2F2F;
  color: #FFFFFF;
}

.social-auth-button--ms:focus-visible {
  outline: 2px solid var(--p-primary-color);
  outline-offset: 2px;
}

.social-auth-button__ms-logo {
  width: 21px;
  height: 21px;
  display: block;
  flex: 0 0 auto;
}

.social-auth-button__ms-label {
  margin-left: 12px;
}

/* Generic style for providers we don't yet have official artwork for. */
.social-auth-button--generic {
  width: 100%;
  padding: 0.625rem 1rem;
  border-radius: 0.5rem;
  background: var(--p-content-background);
  border: 1px solid var(--p-content-border-color);
  color: var(--p-text-color);
  font-weight: 500;
  transition: background-color 0.15s ease;
}

.social-auth-button--generic:hover {
  background: color-mix(in srgb, var(--p-primary-color) 6%, var(--p-content-background));
}

.social-auth-button--generic:focus-visible {
  outline: 2px solid var(--p-primary-color);
  outline-offset: 2px;
}
</style>
