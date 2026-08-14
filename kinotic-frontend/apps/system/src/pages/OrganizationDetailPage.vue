<template>
  <div class="flex flex-col">
    <div class="flex items-center gap-3 mb-5">
      <Button icon="pi pi-arrow-left" severity="secondary" text aria-label="Back to organizations"
              @click="router.push({ name: 'organizations' })" />
      <div>
        <h1 class="text-[1.4rem] font-semibold">{{ organization?.name ?? organizationId }}</h1>
        <p class="text-sm text-muted-color mt-0.5">
          <span class="font-mono">{{ organizationId }}</span>
          <template v-if="organization?.description"> — {{ organization.description }}</template>
        </p>
      </div>
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <!-- The flex chain from ConsoleLayout runs through the tabs so each table's flex-1
         shell fills the viewport height regardless of row count, like every list page. -->
    <Tabs v-else value="applications" class="flex flex-1 flex-col">
      <TabList>
        <Tab value="applications">Applications</Tab>
        <Tab value="projects">Projects</Tab>
        <Tab value="members">Members</Tab>
      </TabList>
      <TabPanels class="flex flex-1 flex-col">
        <TabPanel value="applications" class="flex flex-1 flex-col">
          <OrgApplicationsTable :organization-id="organizationId" />
        </TabPanel>
        <TabPanel value="projects" class="flex flex-1 flex-col">
          <OrgProjectsTable :organization-id="organizationId" />
        </TabPanel>
        <TabPanel value="members" class="flex flex-1 flex-col">
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
    organization.value = await Kinotic.systemOrganizations.findOrganizationById(props.organizationId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load organization'
  }
})
</script>
