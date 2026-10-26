<template>
  <div class="flex flex-col">
    <PageHeader title="Vulnerabilities"
                description="Advisories from OSV that affect your projects' SBOMs, newest first. An alert is raised when a project's SBOM or a newly published advisory brings the two together, and clears itself once the project no longer installs an affected version.">
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
                      description="Each alert links to the project's SBOM. Acknowledge marks it as seen; it stays until the project no longer installs an affected version.">
      <VulnerabilityFindingsTable :items="alerts" data-key="id" :loading="loading"
                                  empty-hint="No advisory affects any of your projects' SBOMs.">
        <template #columns>
          <Column header="Project" style="width: 16%">
            <template #body="{ data }">
              <RouterLink :to="{ name: 'project-sbom', params: { applicationId: data.applicationId, projectId: data.projectId } }"
                          class="font-medium text-primary hover:underline">{{ data.projectId }}</RouterLink>
              <div class="text-xs text-muted-color">{{ data.applicationId }}</div>
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

      <TablePaginator v-if="total" class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                      :first="first" :rows="pageSize" :total-records="total"
                      :rows-per-page-options="PAGE_SIZES" @page="onPage" />
    </DashboardSection>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import Message from 'primevue/message'
import type { PageState } from 'primevue/paginator'
import Tag from 'primevue/tag'
import { BellDot, ShieldAlert } from '@lucide/vue'
import { DashboardSection, errorMessage, PageHeader, StatCard, TablePaginator, TimePill, TINTS } from '@kinotic-ai/frontend-common'
import { VulnerabilityFindingsTable } from '@kinotic-ai/frontend-common/management'
import { Direction, Kinotic, Order, Pageable, Sort } from '@kinotic-ai/core'
import type { VulnerabilityAlert } from '@kinotic-ai/management-api'

/**
 * The organization's vulnerability inbox: every alert raised against its projects' SBOMs, newest
 * first, with how many are open and how many no member has acknowledged yet.
 */

const PAGE_SIZES = [25, 50, 100]
// one scan raises many alerts in the same millisecond, so the id orders them for the pages to stay apart
const NEWEST_FIRST = new Sort()
NEWEST_FIRST.orders = [new Order('created', Direction.DESC), new Order('id', Direction.ASC)]

const alerts = ref<VulnerabilityAlert[]>([])
const total = ref<number | null>(null)
const unacknowledged = ref<number | null>(null)
const first = ref(0)
const pageSize = ref(25)
const loading = ref(true)
const acknowledging = ref<string | null>(null)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [page, count] = await Promise.all([
      Kinotic.vulnerabilityAlerts.findAll(Pageable.create(Math.floor(first.value / pageSize.value), pageSize.value, NEWEST_FIRST)),
      Kinotic.vulnerabilityAlerts.countUnacknowledged(),
    ])
    alerts.value = page.content ?? []
    total.value = page.totalElements ?? alerts.value.length
    unacknowledged.value = count
    error.value = null
  } catch (err) {
    error.value = errorMessage(err, 'The vulnerability alerts could not be loaded')
  } finally {
    loading.value = false
  }
}

async function acknowledge(alert: VulnerabilityAlert): Promise<void> {
  acknowledging.value = alert.id
  try {
    await Kinotic.vulnerabilityAlerts.acknowledge(alert.id)
    alert.acknowledged = true
    if (unacknowledged.value) {
      unacknowledged.value--
    }
  } catch (err) {
    error.value = errorMessage(err, 'The alert could not be acknowledged')
  } finally {
    acknowledging.value = null
  }
}

function onPage(state: PageState): void {
  first.value = state.first
  pageSize.value = state.rows
  void load()
}

void load()
</script>
