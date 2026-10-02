<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Jobs</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="inline-flex min-w-0 items-center rounded-full border border-surface-200 bg-surface-100 px-2.5 py-0.5 font-mono text-xs text-surface-600 dark:border-surface-700 dark:bg-surface-800 dark:text-surface-300"><span class="truncate">{{ jobRunId }}</span></span>
      </template>
      <template #actions>
        <Button v-if="owningOrganizationPath" :label="`Open ${owningOrganizationId}`" icon="pi pi-building" severity="secondary" outlined
                @click="router.push(owningOrganizationPath)" />
      </template>
    </PageHeader>

    <JobRunDetail :job-run-id="jobRunId" :organization-id="scope.organizationId" @organization="owningOrganizationId = $event" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

import { PageHeader } from '@kinotic-ai/frontend-common'

import JobRunDetail from '@/components/JobRunDetail.vue'
import { organizationPath, scopePath, type Scope } from '@/util/scope'

/**
 * One job run opened from a Jobs list. The eyebrow leads back to that list; on the platform a
 * run that belongs to an organization also offers the jump into that organization.
 */
const props = defineProps<{
  jobRunId: string
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const router = useRouter()

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const listPath = computed(() => `${scopePath(scope.value)}/jobs`)

const owningOrganizationId = ref<string | null>(null)
const owningOrganizationPath = computed(() => owningOrganizationId.value ? organizationPath(owningOrganizationId.value) : null)
</script>
