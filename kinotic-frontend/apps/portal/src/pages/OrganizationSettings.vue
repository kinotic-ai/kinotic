<template>
  <div class="flex flex-col">
    <PageHeader title="Organization settings" description="Settings that apply to everyone in your organization." />

    <Tabs lazy :value="activeTab" @update:value="selectTab">
      <TabList>
        <Tab v-for="tab in TABS" :key="tab.id" :value="tab.id">
          <span class="flex items-center gap-2">
            <component :is="tab.icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
            {{ tab.label }}
            <Tag v-if="tab.upcoming" value="Soon" severity="info" class="!text-[10px]" />
          </span>
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="integrations">
          <GitHubLinkStatus return-to="/organization-settings" />
        </TabPanel>
        <TabPanel value="notifications">
          <NotificationSettings />
        </TabPanel>
        <TabPanel v-for="tab in upcomingTabs" :key="tab.id" :value="tab.id">
          <FeatureEmptyState badge="coming-soon" :icon="tab.icon" :tint="tab.upcoming.tint" :title="tab.upcoming.title"
                           :description="tab.upcoming.description" :points="tab.upcoming.points"
                           :meanwhile="tab.upcoming.meanwhile">
            <template #preview>
              <div v-if="tab.id === 'authentication'" :class="PREVIEW_CARD">
                <div class="mb-3 text-xs font-semibold text-surface-950 dark:text-surface-0">Sign-in methods</div>
                <div v-for="provider in PROVIDERS" :key="provider.name"
                     class="flex items-center gap-3 border-t border-surface-100 py-2.5 dark:border-surface-800">
                  <img :src="provider.logo" alt="" class="h-5 w-5" />
                  <span class="flex-1 text-xs text-surface-800 dark:text-surface-100">{{ provider.name }}</span>
                  <span :class="['flex h-4 w-7 items-center rounded-full p-0.5', provider.on ? 'justify-end bg-surface-950 dark:bg-surface-0' : 'bg-surface-200 dark:bg-surface-700']">
                    <span class="h-3 w-3 rounded-full bg-surface-0 dark:bg-surface-900" />
                  </span>
                </div>
              </div>

              <div v-else-if="tab.id === 'identity-mapping'" :class="[PREVIEW_CARD, 'grid grid-cols-[1fr_auto_1fr] items-center gap-x-3 gap-y-2.5']">
                <div class="text-[10px] font-semibold uppercase tracking-wider text-surface-400">Provider group</div>
                <span />
                <div class="text-[10px] font-semibold uppercase tracking-wider text-surface-400">Kinotic role</div>
                <template v-for="mapping in MAPPINGS" :key="mapping.group">
                  <span class="truncate rounded-md border border-surface-200 bg-surface-50 px-2 py-1 font-mono text-[11px] text-surface-700 dark:border-surface-700 dark:bg-surface-800 dark:text-surface-200">{{ mapping.group }}</span>
                  <ArrowRight :size="14" :stroke-width="1.75" class="text-surface-400" />
                  <span :class="['w-fit rounded-md px-2 py-1 text-[11px] font-medium', mapping.tint]">{{ mapping.role }}</span>
                </template>
              </div>

              <div v-else-if="tab.id === 'roles'" :class="PREVIEW_CARD">
                <div class="grid grid-cols-[1fr_repeat(3,3rem)] items-center gap-y-2.5 text-[11px]">
                  <span />
                  <span v-for="role in ROLES" :key="role" class="text-center font-semibold text-surface-950 dark:text-surface-0">{{ role }}</span>
                  <template v-for="permission in PERMISSIONS" :key="permission.name">
                    <span class="text-surface-700 dark:text-surface-200">{{ permission.name }}</span>
                    <span v-for="(granted, index) in permission.grants" :key="index" class="flex justify-center">
                      <CircleCheck v-if="granted" :size="16" :stroke-width="2" class="text-green-600 dark:text-green-400" />
                      <Minus v-else :size="16" :stroke-width="2" class="text-surface-300 dark:text-surface-600" />
                    </span>
                  </template>
                </div>
              </div>

              <div v-else :class="PREVIEW_CARD">
                <div class="flex items-start justify-between">
                  <div>
                    <div class="text-[10px] font-semibold uppercase tracking-wider text-surface-400">Current plan</div>
                    <div class="mt-2 h-4 w-24 rounded bg-surface-200 dark:bg-surface-700" />
                  </div>
                  <span class="rounded-md bg-surface-950 px-2 py-1 text-[11px] font-medium text-surface-0 dark:bg-surface-0 dark:text-surface-950">Manage plan</span>
                </div>
                <div class="mt-5 text-[10px] font-semibold uppercase tracking-wider text-surface-400">Usage this month</div>
                <div v-for="(width, index) in USAGE_BARS" :key="index" class="mt-2.5 flex items-center gap-3">
                  <div class="h-2.5 w-16 rounded bg-surface-200 dark:bg-surface-700" />
                  <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                    <div class="h-full rounded-full bg-orange-400" :style="{ width }" />
                  </div>
                </div>
                <div class="mt-5 text-[10px] font-semibold uppercase tracking-wider text-surface-400">Invoices</div>
                <div v-for="row in 3" :key="row" class="mt-2.5 flex items-center gap-3">
                  <CreditCard :size="14" :stroke-width="1.75" class="text-surface-400" />
                  <div class="h-2.5 flex-1 rounded bg-surface-200 dark:bg-surface-700" />
                  <div class="h-2.5 w-10 rounded bg-surface-200 dark:bg-surface-700" />
                </div>
              </div>
            </template>
          </FeatureEmptyState>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { markRaw, type Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { ArrowRight, Bell, CircleCheck, CreditCard, IdCard, KeyRound, Minus, Plug, ShieldCheck } from '@lucide/vue'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import FeatureEmptyState from '@/components/FeatureEmptyState.vue'
import GitHubLinkStatus from '@/components/GitHubLinkStatus.vue'
import NotificationSettings from '@/components/NotificationSettings.vue'
import { useQueryTab } from '@/composables/useQueryTab'
import { DOCUMENTATION_URL } from '@/util/externalLinks'
import githubLogo from '@/assets/github-icon.svg'
import googleLogo from '@/assets/google-icon.svg'
import keycloakLogo from '@/assets/keycloak-icon.svg'
import microsoftLogo from '@/assets/microsoft_online-icon.svg'
import oktaLogo from '@/assets/okta-icon.svg'

/** What the empty state of a tab that is not built yet says about it. */
interface UpcomingFeature {
  /** One of TINTS. */
  tint: string
  title: string
  description: string
  points: string[]
  meanwhile: { label: string, to?: RouteLocationRaw, href?: string }
}

/** A tab of the settings page; one with upcoming set is not built yet and says what it will hold. */
interface SettingsTab {
  id: 'integrations' | 'notifications' | 'authentication' | 'identity-mapping' | 'roles' | 'billing'
  label: string
  icon: Component
  upcoming?: UpcomingFeature
}

const TABS: SettingsTab[] = [
  { id: 'integrations', label: 'Integrations', icon: markRaw(Plug) },
  { id: 'notifications', label: 'Notifications', icon: markRaw(Bell) },
  {
    id: 'authentication', label: 'Authentication providers', icon: markRaw(KeyRound),
    upcoming: {
      tint: TINTS.blue,
      title: 'Bring your own sign-in',
      description: 'Configure the identity providers your members sign in with, so access follows the accounts your organization already manages.',
      points: [
        'Connect an OpenID Connect provider such as Okta, Microsoft Entra ID, Google or Keycloak',
        'Choose which sign-in methods your members see',
        'Keep GitHub and email sign-in alongside your own provider'
      ],
      meanwhile: { label: 'link GitHub in Integrations', to: { query: {} } }
    }
  },
  {
    id: 'identity-mapping', label: 'Identity mapping', icon: markRaw(IdCard),
    upcoming: {
      tint: TINTS.purple,
      title: 'Map identities to roles',
      description: 'Map the users and groups of your identity provider to your organization\'s members and roles, so people arrive with the right access.',
      points: [
        'Turn provider groups into Kinotic roles',
        'Match external accounts to the members you already have',
        'Keep access in step as people join or leave groups'
      ],
      meanwhile: { label: 'manage your members', to: '/members' }
    }
  },
  {
    id: 'roles', label: 'Roles & permissions', icon: markRaw(ShieldCheck),
    upcoming: {
      tint: TINTS.green,
      title: 'Decide who can do what',
      description: 'Define roles and control what each one can do across your organization\'s applications and projects.',
      points: [
        'Group permissions into roles you name',
        'Scope access to an application or a project',
        'See every member\'s access at a glance'
      ],
      meanwhile: { label: 'review your members', to: '/members' }
    }
  },
  {
    id: 'billing', label: 'Billing & plan', icon: markRaw(CreditCard),
    upcoming: {
      tint: TINTS.orange,
      title: 'Your plan and usage, in one place',
      description: 'Review your subscription, what it includes, and how your organization uses it.',
      points: [
        'See your plan and what it includes',
        'Follow usage across your applications',
        'Manage payment details and download invoices'
      ],
      meanwhile: { label: 'read the documentation', href: DOCUMENTATION_URL }
    }
  }
]

// The previews are illustrations of the screens to come, drawn from these placeholders
const PREVIEW_CARD = 'rounded-xl border border-surface-200 bg-surface-0 p-4 shadow-sm dark:border-surface-700 dark:bg-surface-900'
const PROVIDERS = [
  { name: 'Okta', logo: oktaLogo, on: true },
  { name: 'Microsoft Entra ID', logo: microsoftLogo, on: true },
  { name: 'Google', logo: googleLogo, on: false },
  { name: 'Keycloak', logo: keycloakLogo, on: false },
  { name: 'GitHub', logo: githubLogo, on: true }
]
const MAPPINGS = [
  { group: 'engineering', role: 'Developer', tint: TINTS.blue },
  { group: 'platform-admins', role: 'Admin', tint: TINTS.purple },
  { group: 'design', role: 'Viewer', tint: TINTS.green },
  { group: 'contractors', role: 'Viewer', tint: TINTS.green }
]
const ROLES = ['Admin', 'Dev', 'Viewer']
const PERMISSIONS = [
  { name: 'Manage applications', grants: [true, false, false] },
  { name: 'Deploy projects', grants: [true, true, false] },
  { name: 'Invite members', grants: [true, false, false] },
  { name: 'View dashboards', grants: [true, true, true] }
]
const USAGE_BARS = ['72%', '45%', '28%']

const upcomingTabs = TABS.filter((tab): tab is SettingsTab & { upcoming: UpcomingFeature } => tab.upcoming !== undefined)

const activeTab = useQueryTab(TABS.map(tab => tab.id))

function selectTab(value: string | number): void {
  const tab = TABS.find(t => t.id === value)
  if (tab) {
    activeTab.value = tab.id
  }
}
</script>
