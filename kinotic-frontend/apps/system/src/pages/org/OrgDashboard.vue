<template>
  <div>
    <PageHeader title="Dashboard" description="The organization at a glance." />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
      <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
    </div>

    <div class="mt-6 flex flex-col gap-2 rounded-lg border border-surface p-4">
      <h2 class="text-base font-semibold">Details</h2>
      <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
        <dt class="text-muted-color">Id</dt>
        <dd class="font-mono">{{ organizationId }}</dd>
        <dt class="text-muted-color">Description</dt>
        <dd>{{ organization?.description || '—' }}</dd>
        <dt class="text-muted-color">Created</dt>
        <dd>{{ formatEpochDate(organization?.created ?? null) }}</dd>
      </dl>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Organization } from '@kinotic-ai/os-api'
import { DatetimeUtil, PageHeader } from '@kinotic-ai/frontend-common'

import StatTile from '@/components/StatTile.vue'

const props = defineProps<{
  organizationId: string
}>()

const organization = ref<Organization | null>(null)
const applicationCount = ref<number | null>(null)
const projectCount = ref<number | null>(null)
const memberCount = ref<number | null>(null)
const inviteCount = ref<number | null>(null)
const error = ref<string | null>(null)

const formatEpochDate = DatetimeUtil.formatEpochDate

const stats = computed(() => [
  {
    label: 'Applications',
    value: applicationCount.value?.toString() ?? '—',
    description: 'Applications owned by this organization',
    to: { name: 'org-applications', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Projects',
    value: projectCount.value?.toString() ?? '—',
    description: 'Projects across all of its applications',
    to: { name: 'org-projects', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Members',
    value: memberCount.value?.toString() ?? '—',
    description: 'People with access to this organization',
    to: { name: 'org-members', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Pending invites',
    value: inviteCount.value?.toString() ?? '—',
    description: 'Invitations awaiting acceptance',
    to: { name: 'org-members', params: { organizationId: props.organizationId } }
  }
])

async function load() {
  error.value = null
  organization.value = null
  applicationCount.value = projectCount.value = memberCount.value = inviteCount.value = null
  const orgId = props.organizationId
  // A one-row page carries the full count in totalElements
  const firstPage = Pageable.create(0, 1)
  try {
    const [org, apps, projects, members, invites] = await Promise.all([
      Kinotic.systemOrganizations.findOrganizationById(orgId),
      Kinotic.systemOrganizations.findApplications(orgId, firstPage),
      Kinotic.systemOrganizations.findProjects(orgId, firstPage),
      Kinotic.systemOrganizations.findMembers(orgId, null, firstPage),
      Kinotic.systemOrganizations.findPendingInvites(orgId, null, firstPage)
    ])
    organization.value = org
    applicationCount.value = apps.totalElements ?? 0
    projectCount.value = projects.totalElements ?? 0
    memberCount.value = members.totalElements ?? 0
    inviteCount.value = invites.totalElements ?? 0
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load organization dashboard'
  }
}

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, load)

onMounted(load)
</script>
