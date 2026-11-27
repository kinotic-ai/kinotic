<template>
  <div class="flex flex-col">
    <PageHeader title="Vulnerabilities"
                :description="`Advisories from OSV that affect ${scope.sboms}, newest first. An alert is raised when a project's SBOM or a newly published advisory brings the two together, and clears itself once the project no longer installs an affected version.`">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="mb-4 grid gap-4 sm:grid-cols-2">
      <StatCard :icon="ShieldAlert" :tint="total ? TINTS.red : TINTS.green" label="Open alerts" :value="total"
                detail="advisories affecting a project" :loading="loading && total === null" />
      <StatCard :icon="BellDot" :tint="unacknowledged ? TINTS.orange : TINTS.green" label="Unacknowledged" :value="unacknowledged"
                detail="not yet marked as seen" :loading="loading && unacknowledged === null" />
    </div>

    <DashboardSection :icon="ShieldAlert" :tint="TINTS.red" title="Alerts" :count="total ?? undefined"
                      :description="`${projectId === null ? 'Each alert links to the SBOM of its project. ' : ''}Acknowledge marks an alert as seen; it stays until the project no longer installs an affected version.`">
      <VulnerabilityFindingsTable :items="alerts" data-key="id" :loading="loading"
                                  :empty-hint="`No advisory affects ${scope.sboms}.`">
        <template #columns>
          <Column v-if="projectId === null" header="Project" style="width: 16%">
            <template #body="{ data }">
              <RouterLink :to="{ name: 'project-sbom', params: { applicationId: data.applicationId, projectId: data.projectId } }"
                          class="font-medium text-primary hover:underline">{{ data.projectId }}</RouterLink>
              <div v-if="applicationId === null" class="text-xs text-muted-color">{{ data.applicationId }}</div>
            </template>
          </Column>
          <Column header="Raised" style="width: 10rem">
            <template #body="{ data }"><TimePill :date="data.created" /></template>
          </Column>
          <Column style="width: 9rem">
            <template #body="{ data }">
              <Tag v-if="data.acknowledged" value="Seen" severity="secondary" />
              <Button v-else label="Acknowledge" size="small" text :loading="acknowledging === data.id" @click="acknowledge(data)" />
            </template>
          </Column>
        </template>
      </VulnerabilityFindingsTable>

      <p v-if="total !== null && total > DEEPEST_PAGED" class="border-t border-surface-200 px-5 py-3 text-sm text-muted-color dark:border-surface-700">
        The newest {{ DEEPEST_PAGED.toLocaleString() }} of {{ total.toLocaleString() }} alerts are listed.
      </p>
      <TablePaginator v-if="total" class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                      :first="first" :rows="pageSize" :total-records="paged"
                      :rows-per-page-options="PAGE_SIZES" @page="onPage" />
    </DashboardSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { BellDot, ShieldAlert } from '@lucide/vue'
import { DashboardSection, errorMessage, PageHeader, StatCard, TablePaginator, TimePill, TINTS, usePaging } from '@kinotic-ai/frontend-common'
import { VulnerabilityFindingsTable } from '@kinotic-ai/frontend-common/management'
import { Direction, Kinotic, Order, type Page, Pageable, Sort } from '@kinotic-ai/core'
import type { VulnerabilityAlert } from '@kinotic-ai/management-api'

/**
 * A vulnerability inbox: every alert raised against the SBOMs of the organization's projects (both
 * ids null), of one application's projects, or of one project, newest first, with how many are open
 * and how many no member has acknowledged yet.
 */
const props = withDefaults(defineProps<{
  applicationId?: string | null
  projectId?: string | null
}>(), {
  applicationId: null,
  projectId: null,
})

const PAGE_SIZES = [25, 50, 100]
// how deep into its results a search reads, which every page size above divides, so the last page ends on it
const DEEPEST_PAGED = 10_000
// one scan raises many alerts in the same millisecond, so the id orders them for the pages to stay apart
const NEWEST_FIRST = new Sort()
NEWEST_FIRST.orders = [new Order('created', Direction.DESC), new Order('id', Direction.ASC)]

// what the inbox lists, as the page states it and as the service reads it
const scope = computed(() => {
  const service = Kinotic.vulnerabilityAlerts
  const { applicationId, projectId } = props
  let ret: {
    sboms: string
    findPage: (pageable: Pageable) => Promise<Page<VulnerabilityAlert>>
    countUnacknowledged: () => Promise<number>
  }
  if (projectId !== null) {
    ret = {
      sboms: "the project's SBOM",
      findPage: pageable => service.findAllForProject(projectId, pageable),
      countUnacknowledged: () => service.countUnacknowledgedForProject(projectId),
    }
  } else if (applicationId !== null) {
    ret = {
      sboms: "the application's projects' SBOMs",
      findPage: pageable => service.findAllForApplication(applicationId, pageable),
      countUnacknowledged: () => service.countUnacknowledgedForApplication(applicationId),
    }
  } else {
    ret = {
      sboms: "your projects' SBOMs",
      findPage: pageable => service.findAll(pageable),
      countUnacknowledged: () => service.countUnacknowledged(),
    }
  }
  return ret
})

const alerts = ref<VulnerabilityAlert[]>([])
const total = ref<number | null>(null)
const unacknowledged = ref<number | null>(null)
// the alerts the paginator offers pages for
const paged = computed(() => Math.min(total.value ?? 0, DEEPEST_PAGED))
const { first, pageSize, page: pageShown, onPage } = usePaging(25, () => void load())
const loading = ref(true)
const acknowledging = ref<string | null>(null)
const error = ref<string | null>(null)

// each kind of request is counted, so only the response to the latest of its kind is shown
let latestLoad = 0
let latestCount = 0

async function load(): Promise<void> {
  const current = ++latestLoad
  loading.value = true
  try {
    const [page] = await Promise.all([
      scope.value.findPage(Pageable.create(pageShown.value, pageSize.value, NEWEST_FIRST)),
      loadUnacknowledged(),
    ])
    if (current === latestLoad) {
      alerts.value = page.content ?? []
      total.value = page.totalElements ?? alerts.value.length
      error.value = null
    }
  } catch (err) {
    if (current === latestLoad) {
      error.value = errorMessage(err, 'The vulnerability alerts could not be loaded')
    }
  } finally {
    if (current === latestLoad) {
      loading.value = false
    }
  }
}

async function loadUnacknowledged(): Promise<void> {
  const current = ++latestCount
  const count = await scope.value.countUnacknowledged()
  if (current === latestCount) {
    unacknowledged.value = count
  }
}

async function acknowledge(alert: VulnerabilityAlert): Promise<void> {
  acknowledging.value = alert.id
  try {
    await Kinotic.vulnerabilityAlerts.acknowledge(alert.id)
    alert.acknowledged = true
  } catch (err) {
    error.value = errorMessage(err, 'The alert could not be acknowledged')
    return
  } finally {
    acknowledging.value = null
  }
  // read again rather than decremented, since a refresh under way may already count the acknowledgement
  try {
    await loadUnacknowledged()
  } catch (err) {
    error.value = errorMessage(err, 'The unacknowledged alerts could not be counted')
  }
}

// another application or project opened in the same route starts over at its first page
watch(() => [props.applicationId, props.projectId], () => {
  first.value = 0
  void load()
}, { immediate: true })
</script>
