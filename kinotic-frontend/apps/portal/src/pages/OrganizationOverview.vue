<template>
  <div class="flex flex-col">
    <PageHeader title="Overview" description="Your organization at a glance, and what needs looking at.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <!-- What needs checking first opens the page: the organization's vitals and what needs looking at.
         Titled sections follow: the last hour's traffic, then workloads, then jobs -->
    <div class="flex flex-col gap-8">
      <div class="flex flex-col gap-4">
        <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
          <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
        </div>
        <AttentionList :items="attention" />
      </div>

      <DashboardSection title="Traffic · last hour" description="The calls your organization's services answered.">
        <div class="grid gap-4 md:grid-cols-3">
          <StatTile v-for="stat in trafficStats" :key="stat.label" v-bind="stat" />
        </div>
      </DashboardSection>

      <DashboardSection title="Workloads">
        <div class="grid gap-4 lg:grid-cols-2">
          <WorkloadStateCard :workloads="workloads" description="Your organization's workloads." view-all-to="/workloads" />
          <AllocationCard title="Capacity by application"
                          description="What each application's running workloads hold of the platform's nodes."
                          :allocations="allocations" :owner-route="applicationRoute" view-all-to="/workloads" />
        </div>
      </DashboardSection>

      <DashboardSection title="Jobs">
        <div class="grid gap-4 lg:grid-cols-3">
          <JobRunsByDayChart :runs="runs" :days="RUN_WINDOW_DAYS" view-all-to="/jobs" class="lg:col-span-2" />
          <JobOutcomesCard :runs="runs" :days="RUN_WINDOW_DAYS" view-all-to="/jobs" />
        </div>
        <RecentRunsTable :runs="recentRuns" :scope="scope" jobs-path="/jobs" />
      </DashboardSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, WorkloadStatus, type JobRun, type Workload } from '@kinotic-ai/management-api'
import { AllocationCard, AttentionList, DashboardSection, JobOutcomesCard, JobRunsByDayChart, ORGANIZATION_OWNER, PageHeader,
         RecentRunsTable, StatTile, WorkloadStateCard, allocationBy, applicationOwnerOf, errorMessage, failedRunAttention,
         failedWorkloadAttention, scanJobRuns, useTrafficStats, type Stat, type ViewScope } from '@kinotic-ai/frontend-common'

import { USER_STATE } from '@/states/IUserState'
import { applicationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/**
 * The organization's landing dashboard: how much it holds and what needs looking at, then the
 * traffic its services answered in the last hour, the state of its workloads and what its
 * applications hold of the platform's nodes, and how its job runs went. Each tile and card
 * leads to the page with the detail.
 */

const DAY_MS = 24 * 60 * 60 * 1000
/** How far back the runs chart and the recent-runs list look. */
const RUN_WINDOW_DAYS = 7
const RECENT_RUN_COUNT = 5

const scope = computed<ViewScope>(() => ({ organizationId: USER_STATE.getOrganizationId() }))

const workloads = ref<Workload[]>([])
const runs = ref<JobRun[]>([])
const applicationCount = ref<number | null>(null)
const projectCount = ref<number | null>(null)
const memberCount = ref<number | null>(null)
const inviteCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const { stats: trafficStats, load: loadTraffic } = useTrafficStats(() => ({
  organizationId: USER_STATE.getOrganizationId(),
  filter: { applicationId: null },
  to: '/observability'
}))

const attention = computed(() => [
  ...failedRunAttention(runs.value, scope.value, '/jobs'),
  ...failedWorkloadAttention(workloads.value, scope.value, '/workloads')
])

const recentRuns = computed(() => runs.value.slice(0, RECENT_RUN_COUNT))

const allocations = computed(() => allocationBy(workloads.value, applicationOwnerOf))

function applicationRoute(owner: string): string | undefined {
  return owner === ORGANIZATION_OWNER ? undefined : applicationPath(owner)
}

function plural(count: number | null, noun: string): string {
  return `${count ?? '—'} ${noun}${count === 1 ? '' : 's'}`
}

const stats = computed<Stat[]>(() => {
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  return [
    {
      label: 'Applications',
      value: applicationCount.value?.toString() ?? '—',
      description: `${plural(projectCount.value, 'project')} across them`,
      to: '/applications',
      icon: 'pi-th-large',
      accent: 'sky'
    },
    {
      label: 'Members',
      value: memberCount.value?.toString() ?? '—',
      description: `${plural(inviteCount.value, 'invitation')} pending`,
      to: '/members',
      icon: 'pi-users',
      accent: 'green'
    },
    {
      label: 'Workloads',
      value: `${running}`,
      description: `running of ${workloads.value.length}`,
      to: '/workloads',
      icon: 'pi-box',
      accent: 'amber'
    },
    {
      label: `Jobs · ${RUN_WINDOW_DAYS} d`,
      value: `${runs.value.length}`,
      description: `${runningRuns} running now`,
      to: '/jobs',
      icon: 'pi-list-check',
      accent: 'violet'
    }
  ]
})

// Each source loads on its own so one that fails leaves the others standing
async function load() {
  loading.value = true
  error.value = null
  const failures: string[] = []
  // A one-row page carries the full count in totalElements
  const firstPage = Pageable.create(0, 1)
  await Promise.all([
    Kinotic.applications.count().then(count => { applicationCount.value = count })
           .catch(err => failures.push(errorMessage(err, 'Failed to count applications'))),
    Kinotic.projects.count().then(count => { projectCount.value = count })
           .catch(err => failures.push(errorMessage(err, 'Failed to count projects'))),
    Kinotic.members.findMembers(null, firstPage).then(page => { memberCount.value = page.totalElements ?? 0 })
           .catch(err => failures.push(errorMessage(err, 'Failed to count members'))),
    Kinotic.members.findPendingInvites(null, firstPage).then(page => { inviteCount.value = page.totalElements ?? 0 })
           .catch(err => failures.push(errorMessage(err, 'Failed to count invitations'))),
    scanWorkloads(scope.value).then(list => { workloads.value = list })
                              .catch(err => failures.push(errorMessage(err, 'Failed to load workloads'))),
    scanJobRuns({ organizationId: scope.value.organizationId, since: Date.now() - RUN_WINDOW_DAYS * DAY_MS })
        .then(list => { runs.value = list })
        .catch(err => failures.push(errorMessage(err, 'Failed to load job runs'))),
    loadTraffic()
  ])
  error.value = failures.length > 0 ? failures.join('. ') : null
  loading.value = false
}

onMounted(load)
</script>
