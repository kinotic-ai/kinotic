<script setup lang="ts">
import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application, IOrganizationParticipant, UserParticipantIdentity } from '@kinotic-ai/management-api'
import { computed, onMounted, ref } from 'vue'
import { logout } from '../session'
import StatTile from './StatTile.vue'

const props = defineProps<{
    participant: IOrganizationParticipant
}>()

/** The most of anything the dashboard lists; an organization past this is a job for the portal's paged tables. */
const PAGE_SIZE = 100

const profile = ref<UserParticipantIdentity | null>(null)
const applications = ref<Application[]>([])
const projectCounts = ref<Map<string, number>>(new Map())
const members = ref<UserParticipantIdentity[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const projectTotal = computed(() => [...projectCounts.value.values()].reduce((sum, count) => sum + count, 0))
const signedInAs = computed(() => profile.value?.displayName || profile.value?.email || props.participant.id)

onMounted(load)

async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
        const [me, applicationPage, memberPage] = await Promise.all([
            Kinotic.profile.findMyProfile(),
            Kinotic.applications.findAll(Pageable.create(0, PAGE_SIZE)),
            // null selects the organization's own members rather than an application's
            Kinotic.members.findMembers(null, Pageable.create(0, PAGE_SIZE)),
        ])
        profile.value = me
        applications.value = applicationPage.content ?? []
        members.value = memberPage.content ?? []
        const counts = await Promise.all(applications.value.map(async application =>
            [application.id, await Kinotic.projects.countForApplication(application.id)] as const))
        projectCounts.value = new Map(counts)
    } catch (reason) {
        error.value = reason instanceof Error ? reason.message : 'Loading the organization failed'
    } finally {
        loading.value = false
    }
}
</script>

<template>
  <section class="dashboard">
    <header class="dashboard__header">
      <div>
        <p class="label">Kinotic OS</p>
        <h1 class="dashboard__title neon-cyan pulse">Org Dashboard</h1>
      </div>
      <div class="dashboard__identity">
        <span class="chip"><span class="chip__dot chip__dot--magenta"></span><span class="label">org</span><span class="mono">{{ participant.organizationId }}</span></span>
        <span class="chip"><span class="chip__dot"></span>{{ signedInAs }}</span>
        <button class="button button--ghost" type="button" @click="logout">Sign out</button>
      </div>
    </header>

    <p v-if="error" class="alert">
      {{ error }}
      <button class="button button--ghost dashboard__retry" type="button" @click="load">Retry</button>
    </p>

    <div class="dashboard__stats">
      <StatTile label="Applications" :value="loading ? null : applications.length" accent="cyan" />
      <StatTile label="Projects" :value="loading ? null : projectTotal" accent="magenta" />
      <StatTile label="Members" :value="loading ? null : members.length" accent="lime" />
    </div>

    <div class="dashboard__grid">
      <section class="panel">
        <h2 class="dashboard__panel-title neon-magenta">Applications</h2>
        <p v-if="loading" class="empty">loading…</p>
        <p v-else-if="applications.length === 0" class="empty">No applications yet</p>
        <table v-else class="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Id</th>
              <th>Projects</th>
              <th>Tenancy</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="application in applications" :key="application.id">
              <td>
                <div>{{ application.name }}</div>
                <div v-if="application.description" class="muted">{{ application.description }}</div>
              </td>
              <td class="mono">{{ application.id }}</td>
              <td class="mono">{{ projectCounts.get(application.id) ?? '—' }}</td>
              <td>{{ application.tenantPerUser ? 'tenant per user' : 'shared' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="panel">
        <h2 class="dashboard__panel-title neon-lime">Members</h2>
        <p v-if="loading" class="empty">loading…</p>
        <p v-else-if="members.length === 0" class="empty">No members yet</p>
        <table v-else class="table">
          <thead>
            <tr>
              <th>Member</th>
              <th>Auth</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in members" :key="member.id ?? member.email">
              <td>
                <div>{{ member.displayName || member.email }}</div>
                <div v-if="member.displayName" class="muted">{{ member.email }}</div>
              </td>
              <td class="mono">{{ member.authType ?? '—' }}</td>
              <td :class="member.enabled ? 'neon-lime' : 'muted'">{{ member.enabled ? 'enabled' : 'disabled' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.dashboard__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.dashboard__title {
  font-size: 28px;
}

.dashboard__identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.dashboard__retry {
  margin-left: 12px;
  padding: 6px 12px;
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.dashboard__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.dashboard__panel-title {
  font-size: 14px;
  padding: 16px 12px 8px;
}
</style>
