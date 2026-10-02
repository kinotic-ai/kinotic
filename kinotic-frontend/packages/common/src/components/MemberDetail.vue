<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center gap-4 rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-800/30">
      <InitialsTile :name="member.displayName || member.email" :index="index" size="lg" />
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h2 class="truncate text-lg font-semibold text-surface-950 dark:text-surface-0">{{ member.displayName || member.email }}</h2>
          <Tag :value="member.status" :severity="statusSeverity(member.status)" />
        </div>
        <p v-if="member.displayName" class="mt-0.5 truncate text-sm text-muted-color">{{ member.email }}</p>
      </div>
    </div>

    <DashboardSection :icon="UserRound" :tint="tint" title="About">
      <div class="px-5 pb-2">
        <FactList :facts="facts" />
      </div>
    </DashboardSection>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import Tag from 'primevue/tag'
import { CalendarPlus, Hash, KeyRound, Mail, ShieldCheck, UserRound } from '@lucide/vue'

import type { MemberSummary } from '../types/MemberSummary'
import DatetimeUtil from '../util/DatetimeUtil'
import { authTypeLabel } from '../util/helpers'
import { statusSeverity } from './useCrudTablePage'
import DashboardSection from './DashboardSection.vue'
import FactList from './FactList.vue'
import InitialsTile from './InitialsTile.vue'

/** One person from a members or users list: who they are, their standing, and how they sign in. */
const props = withDefaults(defineProps<{
  member: MemberSummary
  /** Classes of the section's icon tile, one of TINTS. */
  tint: string
  /** The person's place in the list, which picks the avatar's colour as the list does. */
  index?: number
}>(), {
  index: 0
})

const facts = computed(() => {
  const member = props.member
  return [
    { label: 'Email', icon: markRaw(Mail), value: member.email, mono: true },
    { label: 'Signs in with', icon: markRaw(member.authType === 'OIDC' ? ShieldCheck : KeyRound),
      value: member.authType ? authTypeLabel(member.authType) : null },
    { label: member.invite ? 'Invited' : 'Joined', icon: markRaw(CalendarPlus),
      value: member.created ? DatetimeUtil.formatDateTime(member.created) : null },
    { label: member.invite ? 'Invitation id' : 'User id', icon: markRaw(Hash), value: member.id, mono: true }
  ]
})
</script>
