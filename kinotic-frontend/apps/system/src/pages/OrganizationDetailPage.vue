<template>
  <div class="org-detail">
    <div class="org-detail__header">
      <Button icon="pi pi-arrow-left" severity="secondary" text aria-label="Back to organizations"
              @click="router.push({ name: 'organizations' })" />
      <div>
        <h1 class="org-detail__title">{{ organization?.name ?? organizationId }}</h1>
        <p class="org-detail__subtitle">
          <span class="font-mono">{{ organizationId }}</span>
          <template v-if="organization?.description"> — {{ organization.description }}</template>
        </p>
      </div>
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <Tabs v-else value="applications">
      <TabList>
        <Tab value="applications">Applications</Tab>
        <Tab value="projects">Projects</Tab>
        <Tab value="members">Members</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="applications">
          <OrgApplicationsTable :organization-id="organizationId" />
        </TabPanel>
        <TabPanel value="projects">
          <OrgProjectsTable :organization-id="organizationId" />
        </TabPanel>
        <TabPanel value="members">
          <OrgMembersTable :organization-id="organizationId" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'

import { Kinotic } from '@kinotic-ai/core'
import type { Organization } from '@kinotic-ai/os-api'

import OrgApplicationsTable from '@/components/org/OrgApplicationsTable.vue'
import OrgMembersTable from '@/components/org/OrgMembersTable.vue'
import OrgProjectsTable from '@/components/org/OrgProjectsTable.vue'

const props = defineProps<{
  organizationId: string
}>()

const router = useRouter()

const organization = ref<Organization | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    organization.value = await Kinotic.organizations.findById(props.organizationId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load organization'
  }
})
</script>

<style scoped>
.org-detail__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}

.org-detail__title {
  font-size: 1.4rem;
  font-weight: 600;
}

.org-detail__subtitle {
  font-size: 0.85rem;
  color: var(--p-text-muted-color);
  margin-top: 0.15rem;
}
</style>
