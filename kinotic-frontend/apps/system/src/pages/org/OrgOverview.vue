<template>
  <div>
    <PageHeader title="Overview" description="The organization at a glance." />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
      <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
    </div>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
    <WorkloadStateCard description="The organization's workloads, by state." :organization-id="organizationId" />
    <div class="flex flex-col gap-2 rounded-lg border border-surface p-4">
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
    <div class="flex flex-col gap-2 rounded-lg border border-surface p-4">
      <div class="flex items-center justify-between gap-2">
        <h2 class="text-base font-semibold">Provisioning</h2>
        <Button label="Provision again" icon="pi pi-refresh" size="small" severity="secondary" outlined
                :loading="provisioning" :disabled="!organization" @click="provision" />
      </div>
      <p class="text-sm text-muted-color mt-0">
        The storage the organization's deployments publish to, and what serves its UIs from
        it, created by a job when the organization was. Provision again runs that job once
        more; it does whatever an earlier run left undone.
      </p>
      <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
        <dt class="text-muted-color">Storage</dt>
        <dd>
          <span v-if="organization?.storage" :title="organization.storage.status.message ?? undefined">
            <Tag :value="organization.storage.status.type"
                 :severity="deploymentStatusSeverity(organization.storage.status.type)" />
          </span>
          <span v-else>Not provisioned</span>
        </dd>
        <template v-if="organization?.storage?.status.message">
          <dt class="text-muted-color">Reason</dt>
          <dd class="break-words">{{ organization.storage.status.message }}</dd>
        </template>
        <dt class="text-muted-color">Account</dt>
        <dd class="font-mono">{{ organization?.storage?.azureAccountName || '—' }}</dd>
        <dt class="text-muted-color">Endpoint</dt>
        <dd class="font-mono break-all">{{ organization?.storage?.azureBlobEndpoint || '—' }}</dd>
        <dt class="text-muted-color">Last run</dt>
        <dd>
          <router-link v-if="organization?.provisioningJobRunId" class="font-mono text-primary"
                       :to="{ name: 'job-run', params: { jobRunId: organization.provisioningJobRunId } }">
            {{ organization.provisioningJobRunId }}
          </router-link>
          <span v-else>—</span>
        </dd>
      </dl>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Organization } from '@kinotic-ai/management-api'
import { DatetimeUtil, PageHeader, deploymentStatusSeverity, showErrorToast } from '@kinotic-ai/frontend-common'

import StatTile from '@/components/StatTile.vue'
import WorkloadStateCard from '@/components/WorkloadStateCard.vue'

const props = defineProps<{
  organizationId: string
}>()

const toast = useToast()

const organization = ref<Organization | null>(null)
const provisioning = ref(false)
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
    icon: 'pi-th-large',
    accent: 'sky' as const,
    to: { name: 'org-applications', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Projects',
    value: projectCount.value?.toString() ?? '—',
    description: 'Projects across all of its applications',
    icon: 'pi-folder',
    accent: 'violet' as const,
    to: { name: 'org-projects', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Members',
    value: memberCount.value?.toString() ?? '—',
    description: 'People with access to this organization',
    icon: 'pi-users',
    accent: 'green' as const,
    to: { name: 'org-members', params: { organizationId: props.organizationId } }
  },
  {
    label: 'Pending invites',
    value: inviteCount.value?.toString() ?? '—',
    description: 'Invitations awaiting acceptance',
    icon: 'pi-envelope',
    accent: 'amber' as const,
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

async function provision() {
  provisioning.value = true
  try {
    organization.value = await Kinotic.systemOrganizations.provisionOrganization(props.organizationId)
    toast.add({ severity: 'success', summary: 'Provisioning started', life: 3000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to start provisioning', err, { life: 8000 })
  } finally {
    provisioning.value = false
  }
}

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, load)

onMounted(load)
</script>
