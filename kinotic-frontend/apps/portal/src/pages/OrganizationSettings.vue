<template>
  <div class="flex flex-col">
    <PageHeader title="Organization settings" description="Settings that apply to everyone in your organization." />

    <Tabs lazy :value="activeTab" @update:value="selectTab">
      <TabList>
        <Tab v-for="tab in TABS" :key="tab.id" :value="tab.id">
          <span class="flex items-center gap-2">
            <component :is="tab.icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
            {{ tab.label }}
            <Tag v-if="tab.description" value="Soon" severity="secondary" class="!text-[10px]" />
          </span>
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="integrations">
          <section class="max-w-[720px] pt-4">
            <GitHubLinkStatus return-to="/organization-settings" />
          </section>
        </TabPanel>
        <TabPanel v-for="tab in upcomingTabs" :key="tab.id" :value="tab.id">
          <p class="max-w-[640px] pt-4 text-sm text-muted-color">{{ tab.description }} This section is being prepared.</p>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { markRaw, type Component } from 'vue'
import { CreditCard, IdCard, KeyRound, Plug, ShieldCheck } from '@lucide/vue'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { PageHeader } from '@kinotic-ai/frontend-common'
import GitHubLinkStatus from '@/components/GitHubLinkStatus.vue'
import { useQueryTab } from '@/composables/useQueryTab'

/** A tab of the settings page; one with a description is not built yet and says what it will hold. */
interface SettingsTab {
  id: 'integrations' | 'authentication' | 'identity-mapping' | 'roles' | 'billing'
  label: string
  icon: Component
  description?: string
}

const TABS: SettingsTab[] = [
  { id: 'integrations', label: 'Integrations', icon: markRaw(Plug) },
  { id: 'authentication', label: 'Authentication providers', icon: markRaw(KeyRound), description: 'Configure the identity providers available to this organization.' },
  { id: 'identity-mapping', label: 'Identity mapping', icon: markRaw(IdCard), description: 'Map external identities to your organization users and roles.' },
  { id: 'roles', label: 'Roles & permissions', icon: markRaw(ShieldCheck), description: 'Define roles and control access across your organization.' },
  { id: 'billing', label: 'Billing & plan', icon: markRaw(CreditCard), description: 'Review subscription, billing, and usage details for this organization.' }
]

const upcomingTabs = TABS.filter(tab => tab.description)

const activeTab = useQueryTab(TABS.map(tab => tab.id))

function selectTab(value: string | number): void {
  const tab = TABS.find(t => t.id === value)
  if (tab) {
    activeTab.value = tab.id
  }
}
</script>
