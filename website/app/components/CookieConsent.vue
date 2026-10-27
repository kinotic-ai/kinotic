<script setup lang="ts">
import type { ConsentChoice } from '~/utils/consent'

const props = defineProps<{
  /** The visitor's saved choice, or null on a first visit. A saved choice opens the category toggles. */
  current: ConsentChoice | null
}>()

const emit = defineEmits<{
  save: [choice: ConsentChoice]
}>()

const customizing = ref(props.current !== null)
const analytics = ref(props.current?.analytics ?? false)
const marketing = ref(props.current?.marketing ?? false)

const categories = [
  {
    key: 'analytics',
    label: 'Analytics',
    description: 'Google Analytics and Microsoft Clarity cookies that measure visits and record anonymized sessions.',
    model: analytics,
  },
  {
    key: 'marketing',
    label: 'Marketing',
    description: 'Advertising cookies, and use of visit data by Google and Microsoft for ad measurement and personalization.',
    model: marketing,
  },
]

const save = (choice: ConsentChoice) => emit('save', choice)
</script>

<template>
  <div
    class="consent fixed inset-x-4 bottom-4 z-[100] mx-auto max-w-3xl rounded-2xl border p-5 shadow-2xl sm:p-6"
    role="dialog"
    aria-live="polite"
    aria-label="Cookie consent"
  >
    <p class="consent__text font-[InterRegular] text-[14px] leading-[150%]">
      We use Microsoft Clarity and Google Analytics to understand how visitors
      use our site, and Google and Microsoft may use this data for advertising.
      By default we collect only anonymous, cookieless data. Accept all, reject
      all, or choose by category.
      <a href="/privacy" class="consent__link underline">
        Learn more
      </a>.
    </p>

    <ul v-if="customizing" class="consent__list mt-4 divide-y border-y">
      <li class="flex items-center justify-between gap-6 py-3">
        <div>
          <div class="consent__title font-[InterMedium] text-[14px]">Necessary</div>
          <p class="consent__muted mt-0.5 font-[InterRegular] text-[13px] leading-[150%]">
            Keeps the site working and remembers this choice.
          </p>
        </div>
        <span class="consent__muted shrink-0 font-[InterMedium] text-[12px]">Always on</span>
      </li>
      <li v-for="category in categories" :key="category.key" class="flex items-center justify-between gap-6 py-3">
        <div>
          <div :id="`consent-${category.key}`" class="consent__title font-[InterMedium] text-[14px]">{{ category.label }}</div>
          <p class="consent__muted mt-0.5 font-[InterRegular] text-[13px] leading-[150%]">
            {{ category.description }}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          class="consent__switch"
          :aria-checked="category.model.value"
          :aria-labelledby="`consent-${category.key}`"
          @click="category.model.value = !category.model.value"
        >
          <span class="consent__thumb" />
        </button>
      </li>
    </ul>

    <div class="mt-4 flex flex-wrap items-center justify-end gap-3">
      <button
        v-if="!customizing"
        type="button"
        class="consent__button consent__button--outline"
        @click="customizing = true"
      >
        Customize
      </button>
      <button
        type="button"
        class="consent__button consent__button--outline"
        @click="save({ analytics: false, marketing: false })"
      >
        Reject all
      </button>
      <button
        v-if="customizing"
        type="button"
        class="consent__button consent__button--solid"
        @click="save({ analytics, marketing })"
      >
        Save choices
      </button>
      <button
        type="button"
        class="consent__button consent__button--accent"
        @click="save({ analytics: true, marketing: true })"
      >
        Accept all
      </button>
    </div>
  </div>
</template>

<style scoped>
/* Light on the docs' light theme; dark on their dark theme and on the always-dark marketing pages,
   whose layout marks the root k-canvas-dark. */
.consent {
  --consent-bg: #FFFFFF;
  --consent-border: rgba(0, 0, 0, 0.1);
  --consent-text: #4B4B53;
  --consent-title: #111113;
  --consent-muted: #6B6B73;
  --consent-button-text: #111113;
  --consent-outline: rgba(0, 0, 0, 0.18);
  --consent-hover: rgba(0, 0, 0, 0.04);
  --consent-solid: rgba(0, 0, 0, 0.06);
  --consent-solid-hover: rgba(0, 0, 0, 0.1);
  --consent-switch-off: rgba(0, 0, 0, 0.16);
  border-color: var(--consent-border);
  background: var(--consent-bg);
}

html.dark .consent,
html.k-canvas-dark .consent {
  --consent-bg: #1A1A1C;
  --consent-border: rgba(255, 255, 255, 0.1);
  --consent-text: #AFAFB4;
  --consent-title: #FFFFFF;
  --consent-muted: #8A8A92;
  --consent-button-text: #FFFFFF;
  --consent-outline: rgba(255, 255, 255, 0.2);
  --consent-hover: rgba(255, 255, 255, 0.05);
  --consent-solid: rgba(255, 255, 255, 0.12);
  --consent-solid-hover: rgba(255, 255, 255, 0.18);
  --consent-switch-off: rgba(255, 255, 255, 0.16);
}

.consent__text {
  color: var(--consent-text);
}

.consent__link {
  color: var(--consent-title);
}

.consent__link:hover {
  color: #FF2D7A;
}

.consent__list,
.consent__list > li {
  border-color: var(--consent-border);
}

.consent__title {
  color: var(--consent-title);
}

.consent__muted {
  color: var(--consent-muted);
}

.consent__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 20px;
  border-radius: 9999px;
  font-family: InterMedium, sans-serif;
  font-size: 13px;
  color: var(--consent-button-text);
  transition: background 0.15s ease, transform 0.15s ease;
}

.consent__button--outline {
  border: 1px solid var(--consent-outline);
}

.consent__button--outline:hover {
  background: var(--consent-hover);
}

.consent__button--solid {
  background: var(--consent-solid);
}

.consent__button--solid:hover {
  background: var(--consent-solid-hover);
}

.consent__button--accent {
  color: #fff;
  background: linear-gradient(135deg, #FF2D7A 0%, #FF2A55 100%);
  box-shadow: 0 15px 30px rgba(255, 45, 122, 0.28);
}

.consent__button--accent:hover {
  transform: scale(1.02);
}

.consent__button:focus-visible,
.consent__switch:focus-visible {
  outline: 2px solid #FF2D7A;
  outline-offset: 3px;
}

.consent__switch {
  position: relative;
  flex: none;
  width: 44px;
  height: 24px;
  border-radius: 9999px;
  background: var(--consent-switch-off);
  transition: background 0.15s ease;
}

.consent__switch[aria-checked='true'] {
  background: #FF2D7A;
}

.consent__thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s ease;
}

.consent__switch[aria-checked='true'] .consent__thumb {
  transform: translateX(20px);
}
</style>
