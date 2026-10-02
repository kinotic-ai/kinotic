<template>
  <FormDrawer :visible="visible" title="Notifications"
              description="What happened across your organization in the last 7 days." @close="emit('close')">
    <div class="flex flex-col gap-4">
      <div class="flex items-center justify-between gap-3">
        <StatusChips v-model="view" :chips="chips" />
        <Button label="Mark all as read" severity="secondary" text size="small" :disabled="NOTIFICATION_STATE.unreadCount === 0"
                @click="NOTIFICATION_STATE.markAllRead()" />
      </div>

      <div v-if="!NOTIFICATION_STATE.loaded" class="py-10 text-center text-sm text-muted-color">Loading activity…</div>
      <EmptyChartCharacter v-else-if="listed.length === 0" class="py-10" :title="emptyTitle" :hint="emptyHint" />

      <!-- Entries run the drawer's full width, a divider between each, so the hover reads as one row -->
      <section v-for="day in days" :key="day.label">
        <h3 class="mb-2 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">{{ day.label }}</h3>
        <ul class="-mx-6 divide-y divide-surface-200 border-y border-surface-200 dark:divide-surface-800 dark:border-surface-800">
          <li v-for="item in day.items" :key="item.key">
            <button type="button"
                    class="group flex w-full items-start gap-3 px-6 py-4 text-left transition-colors hover:bg-surface-50 dark:hover:bg-surface-800/60"
                    @click="openItem(item)">
              <span :class="['mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', CATEGORIES[item.category].tint]">
                <component :is="CATEGORIES[item.category].icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <span class="min-w-0 flex-1">
                <span :class="['block text-sm text-surface-950 dark:text-surface-0', NOTIFICATION_STATE.isUnread(item) ? 'font-semibold' : 'font-medium']">
                  {{ item.title }}
                </span>
                <span v-if="item.detail" class="mt-0.5 line-clamp-2 block text-[0.8125rem] text-surface-600 dark:text-surface-300">{{ item.detail }}</span>
                <span class="mt-1 block text-xs text-muted-color" v-tooltip.bottom="formatEpochDateTime(item.at)">
                  {{ CATEGORIES[item.category].label }} · {{ timeOf(item.at) }}
                </span>
              </span>
              <span v-if="NOTIFICATION_STATE.isUnread(item)" class="mt-2 h-2 w-2 shrink-0 rounded-full bg-sky-500" aria-label="Unread" />
            </button>
          </li>
        </ul>
      </section>
    </div>

    <template #footer>
      <Button label="Notification settings" icon="pi pi-cog" severity="secondary" outlined @click="openSettings" />
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="NOTIFICATION_STATE.loading"
              @click="NOTIFICATION_STATE.refresh()" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { DatetimeUtil, EmptyChartCharacter, FormDrawer, StatusChips, type StatusChip } from '@kinotic-ai/frontend-common'

import { CATEGORIES } from '@/notifications/notificationCatalog'
import type { NotificationItem } from '@/notifications/NotificationItem'
import { NOTIFICATION_STATE } from '@/states/INotificationState'

/**
 * The bell's drawer: the organization's recent activity grouped by day, newest first, filtered to
 * everything, the unread, or the failures. Opening an entry marks it read and goes to where it
 * happened; the footer leads to the settings that choose what appears.
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()


const router = useRouter()
// The chip picked: 'unread', 'failures', or null for All, as in every StatusChips row
const view = ref<string | null>(null)

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null, count: NOTIFICATION_STATE.shown.length },
  { label: 'Unread', value: 'unread', count: NOTIFICATION_STATE.unreadCount, severity: 'info' },
  { label: 'Failures', value: 'failures', count: NOTIFICATION_STATE.shown.filter(item => CATEGORIES[item.category].failure).length,
    severity: 'danger' }
])
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const listed = computed(() => NOTIFICATION_STATE.shown.filter(item => {
  let ret: boolean
  if (view.value === 'unread') {
    ret = NOTIFICATION_STATE.isUnread(item)
  } else if (view.value === 'failures') {
    ret = CATEGORIES[item.category].failure
  } else {
    ret = true
  }
  return ret
}))

/** The listed entries in runs of one calendar day: Today, Yesterday, then the date. */
const days = computed(() => {
  const ret: { label: string, items: NotificationItem[] }[] = []
  for (const item of listed.value) {
    const label = DatetimeUtil.formatRelativeDate(item.at)
    const day = ret[ret.length - 1]
    if (day && day.label === label) {
      day.items.push(item)
    } else {
      ret.push({ label, items: [item] })
    }
  }
  return ret
})

const emptyTitle = computed(() => view.value === 'unread' ? "You're all caught up" : 'Nothing to report')
const emptyHint = computed(() => view.value === null
    ? 'No activity in the categories you follow in the last 7 days.'
    : 'Switch to All to see everything from the last 7 days.')

function timeOf(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

function openItem(item: NotificationItem) {
  NOTIFICATION_STATE.markRead(item.key)
  emit('close')
  router.push(item.to)
}

function openSettings() {
  emit('close')
  router.push({ path: '/organization-settings', query: { tab: 'notifications' } })
}

// Opening the drawer reads the feed again, so it never shows only what the last poll saw
watch(() => props.visible, visible => {
  if (visible) {
    void NOTIFICATION_STATE.refresh()
  }
})
</script>
