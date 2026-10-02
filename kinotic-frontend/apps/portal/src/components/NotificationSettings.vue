<template>
  <div class="grid items-start gap-4 pt-4 xl:grid-cols-2">
    <DashboardSection :icon="Bell" :tint="TINTS.green" title="In-app notifications"
                      description="What the bell in the header tells you about. Saved in this browser.">
      <div class="flex flex-col">
        <section v-for="group in groups" :key="group.name" class="border-b border-surface-100 px-5 py-4 last:border-b-0 dark:border-surface-800">
          <h3 class="mb-2 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">{{ group.name }}</h3>
          <div v-for="category in group.categories" :key="category" class="flex items-center gap-3 py-2">
            <span :class="['flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', CATEGORIES[category].tint]">
              <component :is="CATEGORIES[category].icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
            </span>
            <label :for="`notify-${category}`" class="min-w-0 flex-1 cursor-pointer">
              <span class="block text-sm font-medium text-surface-950 dark:text-surface-0">{{ CATEGORIES[category].label }}</span>
              <span class="block text-xs text-muted-color">{{ CATEGORIES[category].description }}</span>
            </label>
            <ToggleSwitch :input-id="`notify-${category}`" :model-value="NOTIFICATION_STATE.enabled[category]"
                          @update:model-value="NOTIFICATION_STATE.setEnabled(category, $event)" />
          </div>
        </section>
      </div>
    </DashboardSection>

    <!-- The digest needs the server to send it on a schedule, so its settings are shown but not yet live -->
    <DashboardSection :icon="Mail" :tint="TINTS.green" title="Email digest"
                      description="A summary of your organization's activity, sent to your inbox.">
      <div class="flex flex-col gap-5 p-5">
        <p class="flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-[0.8125rem] text-sky-800 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-200">
          <Info :size="15" :stroke-width="1.75" class="mt-0.5 shrink-0" aria-hidden="true" />
          Email digests are coming soon. These settings show what you'll be able to choose.
        </p>

        <div>
          <span class="mb-2 block text-sm font-medium text-surface-950 dark:text-surface-0">How often</span>
          <SelectButton :model-value="'weekly'" :options="FREQUENCIES" option-label="label" option-value="value" disabled />
        </div>

        <div>
          <span class="mb-1 block text-sm font-medium text-surface-950 dark:text-surface-0">Sent to</span>
          <span class="font-mono text-[0.8125rem] text-surface-700 dark:text-surface-200">{{ PROFILE_STATE.profile?.email ?? 'your sign-in email' }}</span>
        </div>

        <div>
          <span class="mb-2 block text-sm font-medium text-surface-950 dark:text-surface-0">Include</span>
          <div class="flex flex-col gap-2">
            <label v-for="category in ALL_CATEGORIES" :key="category" class="flex items-center gap-2 text-sm text-surface-500 dark:text-surface-400">
              <Checkbox :model-value="CATEGORIES[category].defaultOn" binary disabled />
              {{ CATEGORIES[category].label }}
            </label>
          </div>
        </div>
      </div>
    </DashboardSection>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import Checkbox from 'primevue/checkbox'
import SelectButton from 'primevue/selectbutton'
import ToggleSwitch from 'primevue/toggleswitch'
import { Bell, Info, Mail } from '@lucide/vue'
import { DashboardSection, TINTS } from '@kinotic-ai/frontend-common'

import { CATEGORIES } from '@/notifications/notificationCatalog'
import { NotificationCategory } from '@/notifications/NotificationCategory'
import { NOTIFICATION_STATE } from '@/states/INotificationState'
import { PROFILE_STATE } from '@/states/IProfileState'

/**
 * The Notifications tab of the organization settings: which kinds of activity the bell reports,
 * and the email digest's options, which are shown ahead of the digest itself.
 */

const ALL_CATEGORIES = Object.values(NotificationCategory)

const FREQUENCIES = [
  { label: 'Off', value: 'off' },
  { label: 'Daily', value: 'daily' },
  { label: 'Weekly', value: 'weekly' }
]

const groups = (['Job runs', 'Deployments'] as const).map(name => ({
  name,
  categories: ALL_CATEGORIES.filter(category => CATEGORIES[category].group === name)
}))

onMounted(() => {
  NOTIFICATION_STATE.start()
  void PROFILE_STATE.load().catch(() => {})
})
</script>
