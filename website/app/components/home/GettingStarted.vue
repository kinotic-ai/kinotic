<script setup lang="ts">
import { trackClarityEvent } from '~/utils/clarity'
import { trackGoogleAnalyticsEvent } from '~/utils/googleAnalytics'

const INSTALL_COMMAND = 'curl -fsSL https://kinotic.ai/install | sh'
const options = [
  { id: 'local', label: 'Local install' },
  { id: 'cloud', label: 'Kinotic Cloud' },
] as const

const id = useId()
const activeIndex = ref(0)
const tabs = useTemplateRef<HTMLButtonElement[]>('tabs')
const copyStatus = ref('')
let copyTimeout: ReturnType<typeof setTimeout> | undefined

function onTabKeydown(event: KeyboardEvent) {
  let next: number
  if (event.key === 'ArrowRight') next = (activeIndex.value + 1) % options.length
  else if (event.key === 'ArrowLeft') next = (activeIndex.value + options.length - 1) % options.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = options.length - 1
  else return

  event.preventDefault()
  activeIndex.value = next
  tabs.value?.[next]?.focus()
}

async function copyCommand() {
  clearTimeout(copyTimeout)
  try {
    await navigator.clipboard.writeText(INSTALL_COMMAND)
    copyStatus.value = 'Copied!'
    copyTimeout = setTimeout(() => { copyStatus.value = '' }, 2500)
    trackInstallIntent('install_command_copied')
  } catch {
    copyStatus.value = 'Select the command to copy.'
  }
}

function trackInstallIntent(name: 'install_command_copied' | 'install_quickstart_opened') {
  trackGoogleAnalyticsEvent(name)
  trackClarityEvent(name)
}

onBeforeUnmount(() => clearTimeout(copyTimeout))
</script>

<template>
  <div class="getting-started" aria-label="Get started with Kinotic">
    <div class="getting-started__tabs" role="tablist" aria-label="Choose how to get started" @keydown="onTabKeydown">
      <button
        v-for="(option, index) in options"
        :id="`${id}-tab-${option.id}`"
        :key="option.id"
        ref="tabs"
        type="button"
        class="getting-started__tab"
        role="tab"
        :aria-selected="activeIndex === index"
        :aria-controls="`${id}-panel-${option.id}`"
        :tabindex="activeIndex === index ? 0 : -1"
        @click="activeIndex = index"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
          <template v-if="option.id === 'local'">
            <rect x="2" y="4" width="20" height="16" rx="2" />
            <path d="m6 9 3 3-3 3m6 0h5" />
          </template>
          <path v-else d="M6 18a5 5 0 0 1-1-9.9A7 7 0 0 1 18.5 8 5 5 0 0 1 18 18Z" />
        </svg>
        {{ option.label }}
        <span v-if="option.id === 'cloud'" class="getting-started__soon">Coming soon</span>
      </button>
    </div>

    <div
      v-show="activeIndex === 0"
      :id="`${id}-panel-local`"
      class="getting-started__body"
      role="tabpanel"
      :aria-labelledby="`${id}-tab-local`"
    >
      <p class="getting-started__intro">Run the whole platform on your machine.</p>
      <div class="getting-started__command">
        <span class="getting-started__prompt" aria-hidden="true">$</span>
        <code>{{ INSTALL_COMMAND }}</code>
        <button type="button" class="getting-started__copy" aria-label="Copy install command" @click="copyCommand">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <rect x="8" y="8" width="12" height="13" rx="2" />
            <path d="M15 8V4a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h4" />
          </svg>
        </button>
      </div>
      <div class="getting-started__details">
        <span>Docker · macOS (Apple silicon) or Linux</span>
        <NuxtLink to="/getting-started/quick-start" @click="trackInstallIntent('install_quickstart_opened')">More info ↗</NuxtLink>
      </div>
      <span class="getting-started__status" role="status">{{ copyStatus }}</span>
    </div>

    <div
      v-show="activeIndex === 1"
      :id="`${id}-panel-cloud`"
      class="getting-started__body"
      role="tabpanel"
      :aria-labelledby="`${id}-tab-cloud`"
    >
      <h2 class="getting-started__heading">Join our waiting list</h2>
      <p class="getting-started__intro">Kinotic, managed for you. Nothing to install.</p>
      <HomeWaitlistForm class="getting-started__waitlist" submit-label="Join waiting list ↗" />
    </div>
  </div>
</template>

<style scoped>
.getting-started {
  max-width: 510px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 9px;
  background: linear-gradient(135deg, rgba(19, 23, 24, 0.91), rgba(11, 12, 14, 0.91));
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  font-family: var(--font-k-body);
}

.getting-started__tabs {
  display: flex;
  gap: 24px;
  padding: 0 19px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.09);
  background: rgba(255, 255, 255, 0.008);
}

.getting-started__tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 1px;
  border: 0;
  background: none;
  color: #919A9E;
  font: 500 12px var(--font-k-mono);
  white-space: nowrap;
  cursor: pointer;
}

.getting-started__tab svg,
.getting-started__copy svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.getting-started__tab[aria-selected='true'] {
  color: var(--color-k-mint);
}

.getting-started__tab[aria-selected='true']::after {
  content: '';
  position: absolute;
  inset: auto 0 -1px;
  height: 2px;
  background: var(--color-k-mint);
}

.getting-started__soon {
  padding: 2px 4px;
  border: 1px solid rgba(236, 31, 82, 0.19);
  border-radius: 3px;
  background: rgba(236, 31, 82, 0.055);
  color: #BCA0A8;
  font-size: 8px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.getting-started__body {
  padding: 20px;
}

.getting-started__intro {
  margin: 0 0 14px;
  color: #A8AFB2;
  font-size: 13px;
  line-height: 1.6;
}

.getting-started__command {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 11px;
  border: 1px solid rgba(40, 254, 180, 0.145);
  border-radius: 5px;
  background: #06090A;
}

.getting-started__command code {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  color: #E1E6E6;
  font: 11.5px var(--font-k-mono);
  white-space: nowrap;
}

.getting-started__prompt {
  color: var(--color-k-mint);
  font: 12px var(--font-k-mono);
  user-select: none;
}

.getting-started__copy {
  display: grid;
  place-items: center;
  flex: none;
  padding: 3px;
  border: 0;
  background: none;
  color: #859693;
  cursor: pointer;
}

.getting-started__copy:hover {
  color: var(--color-k-mint);
}

.getting-started__details {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 13px;
  color: #737E81;
  font-size: 11px;
}

.getting-started__details a {
  color: var(--color-k-mint);
  white-space: nowrap;
}

.getting-started__details a:hover {
  text-decoration: underline;
}

.getting-started__tab:focus-visible,
.getting-started__copy:focus-visible,
.getting-started__details a:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 4px;
}

.getting-started__heading {
  margin: 0 0 6px;
  color: #F5F7F7;
  font-size: 15px;
  font-weight: 500;
}

.getting-started__status {
  color: #B4E9D6;
  font-size: 11px;
}

.getting-started__waitlist :deep(.waitlist__email) {
  padding: 11px 12px;
  font-size: 11px;
}

.getting-started__waitlist :deep(.waitlist__submit) {
  padding: 10px 13px;
  font-size: 11px;
}

.getting-started__waitlist :deep(.waitlist__note) {
  margin-top: 10px;
  font-size: 10px;
}

@media (max-width: 768px) {
  .getting-started {
    max-width: none;
  }

  .getting-started__tabs {
    gap: 19px;
  }

  .getting-started__tab {
    font-size: 11px;
  }

  .getting-started__body {
    padding: 17px;
  }

  .getting-started__command code,
  .getting-started__details {
    font-size: 10px;
  }
}

@media (max-width: 480px) {
  .getting-started__soon {
    display: none;
  }

  .getting-started__command code {
    white-space: normal;
    overflow-wrap: anywhere;
    line-height: 1.7;
  }
}

@media (max-width: 380px) {
  .getting-started__tabs {
    gap: 12px;
    padding: 0 12px;
  }

  .getting-started__tab {
    gap: 5px;
    font-size: 10px;
  }
}
</style>
